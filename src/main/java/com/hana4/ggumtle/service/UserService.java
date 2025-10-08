package com.hana4.ggumtle.service;

import java.util.Random;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hana4.ggumtle.dto.user.UserRequestDto;
import com.hana4.ggumtle.dto.user.UserResponseDto;
import com.hana4.ggumtle.global.error.CustomException;
import com.hana4.ggumtle.global.error.ErrorCode;
import com.hana4.ggumtle.model.entity.user.User;
import com.hana4.ggumtle.repository.RefreshTokenRepository;
import com.hana4.ggumtle.repository.TelCodeValidationRepository;
import com.hana4.ggumtle.repository.UserRepository;
import com.hana4.ggumtle.security.provider.JwtProvider;
import com.hana4.ggumtle.service.sms.SmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
	private final UserRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final MyDataService myDataService;
	private final SmsService smsService;
	private final TelCodeValidationRepository telCodeValidationRepository;
	private final RefreshTokenRepository refreshTokenRepository;

	public User getUserByTel(String tel) {
		return userRepository.findUserByTel(tel)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "해당 전화번호를 사용하는 유저를 찾을 수 없습니다. : " + tel));
	}

	public UserResponseDto.UserInfo register(UserRequestDto.Register userRequestDto) {

		if (userRepository.existsUserByTel(userRequestDto.getTel())) {
			throw new CustomException(ErrorCode.ALREADY_EXISTS, "해당 전화번호를 사용하는 유저가 이미 존재합니다.");
		}

		// 패스워드 해싱 및 새로운 DTO 생성
		String hashedPassword = passwordEncoder.encode(userRequestDto.getPassword());
		UserRequestDto.Register updatedDto = userRequestDto.toBuilder()
			.password(hashedPassword) // 변경된 비밀번호만 설정
			.build();

		User user = userRepository.save(updatedDto.toEntity());

		// MyData 생성 및 저장
		myDataService.createRandomMyData(user);

		return UserResponseDto.UserInfo.from(user);
	}

	public UserResponseDto.TokensWithPermission login(UserRequestDto.Login userRequestDto) {
		// 사용자 정보 조회
		User userInfo = this.getUserByTel(userRequestDto.getTel());

		// password 일치 여부 체크
		if (!passwordEncoder.matches(userRequestDto.getPassword(), userInfo.getPassword()))
			throw new CustomException(ErrorCode.NOT_CORRECT);

		// jwt 토큰 생성
		String accessToken = jwtProvider.generateAccessToken(userInfo.getId());

		// 기존에 가지고 있는 사용자의 refresh token 제거
		refreshTokenRepository.deleteAllTokensByUserId(userInfo.getId());

		// refresh token 생성 후 저장
		String refreshToken = jwtProvider.generateRefreshToken(userInfo.getId());
		refreshTokenRepository.saveRefreshToken(refreshToken, userInfo.getId());

		return UserResponseDto.TokensWithPermission.builder()
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.permission(userInfo.getPermission())
			.build();
	}

	public UserResponseDto.Tokens refresh(UserRequestDto.Refresh userRequestDto) {
		// refresh token 유효성 검증
		checkRefreshToken(userRequestDto.getRefreshToken());

		// refresh token id 조회
		String userId = refreshTokenRepository.getRefreshToken(userRequestDto.getRefreshToken());
		if (userId != null) {
			// 기존에 가지고 있는 사용자의 refresh token 제거
			refreshTokenRepository.deleteRefreshToken(userRequestDto.getRefreshToken());
		}

		// 새로운 access token 생성
		String newAccessToken = jwtProvider.generateAccessToken(userId);

		// 새로운 refresh token 생성 후 저장
		String newRefreshToken = jwtProvider.generateRefreshToken(userId);
		refreshTokenRepository.saveRefreshToken(newRefreshToken, userId);

		return UserResponseDto.Tokens.builder()
			.accessToken(newAccessToken)
			.refreshToken(newRefreshToken)
			.build();
	}

	/**
	 * refresh token 검증
	 */
	private void checkRefreshToken(final String refreshToken) {
		if (Boolean.FALSE.equals(jwtProvider.validateToken(refreshToken)))
			throw new CustomException(ErrorCode.TOKEN_INVALID);
	}

	public UserResponseDto.UserInfo addMyDataPermission(User user) {
		user.addPermission(User.PERMISSION_MYDATA);
		userRepository.save(user);
		return UserResponseDto.UserInfo.from(user);
	}

	public User addSurveyPermission(User user) {
		user.addPermission(User.PERMISSION_SURVEY);
		userRepository.save(user);
		return user;
	}

	public void sendVerificationCode(String userTel) {
		if (userRepository.existsUserByTel(userTel)) {
			throw new CustomException(ErrorCode.ALREADY_EXISTS, "해당 전화번호로 가입된 사용자가 존재합니다.");
		}

		if (telCodeValidationRepository.hasKey(userTel)) {
			throw new CustomException(ErrorCode.SMS_ALREADY_SENT, "인증 코드가 이미 발송되었습니다. 잠시 후 다시 시도해주세요.");
		}

		if (!telCodeValidationRepository.incrementDailyRequestCount(userTel)) {
			throw new CustomException(ErrorCode.DAILY_LIMIT_EXCEEDED, "일일 SMS 인증 요청 한도를 초과했습니다. 내일 다시 시도해주세요.");
		}

		String verificationCode = String.format("%06d", new Random().nextInt(1000000));
		try {
			smsService.sendOne(userTel, verificationCode);
			telCodeValidationRepository.createSmsCertification(userTel, verificationCode);
		} catch (RedisConnectionFailureException e) {
			log.error("Failed to connect to Redis", e);
			throw new CustomException(ErrorCode.REDIS_CONNECTION_FAILURE, "Redis 연결에 실패했습니다.");
		} catch (Exception e) {
			log.error("Error sending SMS or creating certification", e);
			throw new CustomException(ErrorCode.SMS_FAILURE, "SMS 발송 또는 인증 생성에 실패했습니다.");
		}
	}

	public void validateVerificationCode(UserRequestDto.Validation request) {
		try {
			String storedCode = telCodeValidationRepository.getSmsCertification(request.getTel());

			if (storedCode == null) {
				throw new CustomException(ErrorCode.NOT_FOUND, "인증 코드를 찾을 수 없습니다.");
			}

			if (!storedCode.equals(request.getCode())) {
				throw new CustomException(ErrorCode.SMS_VALIDATION_FAILURE, "인증 코드가 일치하지 않습니다.");
			}

			telCodeValidationRepository.removeSmsCertification(request.getTel());
		} catch (RedisConnectionFailureException e) {
			log.error("Failed to connect to Redis", e);
			throw new CustomException(ErrorCode.REDIS_CONNECTION_FAILURE, "Redis 연결에 실패했습니다.");
		} catch (CustomException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error validating verification code", e);
			throw new CustomException(ErrorCode.SMS_FAILURE, "인증 코드 검증 중 오류가 발생했습니다.");
		}
	}

	public UserResponseDto.UserInfo getUserInfo(String userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "해당 유저를 찾을 수 없습니다"));

		return UserResponseDto.UserInfo.from(user);
	}

	public UserResponseDto.UserInfo updateUserInfo(String userId, UserRequestDto.UpdateUser updateUserRequest) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "유저를 찾을 수 없습니다."));

		if (updateUserRequest.getPassword() != null) {
			user.setPassword(passwordEncoder.encode(updateUserRequest.getPassword()));
		}

		if (updateUserRequest.getProfileImageUrl() != null) {
			user.setProfileImageUrl(updateUserRequest.getProfileImageUrl());
		}

		if (updateUserRequest.getNickname() != null) {
			user.setNickname(updateUserRequest.getNickname());
		}

		userRepository.save(user);

		return UserResponseDto.UserInfo.from(user);
	}

	public void deleteUser(String userId) {
		log.info("{} : userId", userId);
		if (!userRepository.existsById(userId)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 유저를 찾을 수 없습니다.");
		}

		userRepository.deleteById(userId);
	}
}
