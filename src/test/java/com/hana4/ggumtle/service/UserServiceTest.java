package com.hana4.ggumtle.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private BCryptPasswordEncoder passwordEncoder;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private MyDataService myDataService;

	@InjectMocks
	private UserService userService;

	@Mock
	private TelCodeValidationRepository telCodeValidationRepository;

	@Mock
	private SmsService smsService;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	// @BeforeEach
	// void setUp() {
	// 	MockitoAnnotations.openMocks(this);
	// }

	@Test
	void testUpdatePermission_Success() {
		// given
		User mockUser = new User();
		mockUser.setPermission((short)0); // initial permission

		// when
		UserResponseDto.UserInfo result = userService.addMyDataPermission(mockUser);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPermission()).isEqualTo((short)1); // check if permission is updated
	}

	@Test
	void testGetUserByTel_UserExists() {
		// given
		String tel = "01012341234";
		User mockUser = new User();
		when(userRepository.findUserByTel(tel)).thenReturn(Optional.of(mockUser));

		// when
		User result = userService.getUserByTel(tel);

		// then
		assertThat(result).isEqualTo(mockUser);
		verify(userRepository).findUserByTel(tel);
	}

	@Test
	void testGetUserByTel_UserNotFound() {
		// given
		String tel = "01012341234";
		when(userRepository.findUserByTel(tel)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userService.getUserByTel(tel))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("해당 전화번호를 사용하는 유저를 찾을 수 없습니다. : " + tel);
		verify(userRepository).findUserByTel(tel);
	}

	@Test
	void testRegister_UserAlreadyExists() {
		// given
		UserRequestDto.Register registerDto = UserRequestDto.Register.builder()
			.name("문서아")
			.tel("01012341234")
			.password("password")
			.birthDate("2000-01-01")
			.gender("f")
			.nickname("익명의고라니")
			.build();
		when(userRepository.existsUserByTel(registerDto.getTel())).thenReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.register(registerDto))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("해당 전화번호를 사용하는 유저가 이미 존재합니다.");
		verify(userRepository).existsUserByTel(registerDto.getTel());
	}

	@Test
	void testRegister_Success() {
		// given
		// UserRequestDto.Register registerDto = new UserRequestDto.Register("01012341234", "password", "name");
		UserRequestDto.Register registerDto = UserRequestDto.Register.builder()
			.name("문서아")
			.tel("01012341234")
			.password("password")
			.birthDate("2000-01-01")
			.gender("f")
			.nickname("익명의고라니")
			.build();
		when(userRepository.existsUserByTel(registerDto.getTel())).thenReturn(false);
		when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("hashedPassword");
		when(userRepository.save(any(User.class))).thenReturn(new User());

		// when
		UserResponseDto.UserInfo result = userService.register(registerDto);

		// then
		assertThat(result).isNotNull();
		verify(passwordEncoder).encode(registerDto.getPassword());
		verify(userRepository).save(any(User.class));
		verify(myDataService).createRandomMyData(any(User.class));
	}

	@Test
	void testLogin_Success() {
		// Given
		String tel = "01012341234";
		String password = "password";
		UserRequestDto.Login loginDto = new UserRequestDto.Login(tel, password);
		User mockUser = new User();
		mockUser.setId("userId");
		mockUser.setPassword("hashedPassword");

		when(userRepository.findUserByTel(tel)).thenReturn(Optional.of(mockUser));
		when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);
		when(jwtProvider.generateAccessToken(any())).thenReturn("accessToken");

		// When
		UserResponseDto.TokensWithPermission result = userService.login(loginDto);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.getAccessToken()).isEqualTo("accessToken");
		verify(jwtProvider).generateAccessToken(any());
	}

	@Test
	void testLogin_InvalidPassword() {
		// given
		String tel = "01012341234";
		String password = "password";
		UserRequestDto.Login loginDto = new UserRequestDto.Login(tel, password);
		User mockUser = new User();
		mockUser.setPassword("hashedPassword");
		when(userRepository.findUserByTel(tel)).thenReturn(Optional.of(mockUser));
		when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> userService.login(loginDto))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining(ErrorCode.NOT_CORRECT.getMessage());
	}

	@Test
	void testRefresh_Success() {
		// Given
		String refreshToken = "validRefreshToken";
		String userId = "userId";
		when(jwtProvider.validateToken(refreshToken)).thenReturn(true);
		when(refreshTokenRepository.getRefreshToken(refreshToken)).thenReturn(userId);
		when(jwtProvider.generateAccessToken(userId)).thenReturn("newAccessToken");
		when(jwtProvider.generateRefreshToken(userId)).thenReturn("newRefreshToken");

		UserRequestDto.Refresh refreshRequest = new UserRequestDto.Refresh(refreshToken);

		// When
		UserResponseDto.Tokens result = userService.refresh(refreshRequest);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.getAccessToken()).isEqualTo("newAccessToken");
		assertThat(result.getRefreshToken()).isEqualTo("newRefreshToken");

		verify(refreshTokenRepository).deleteRefreshToken(refreshToken);
		verify(refreshTokenRepository).saveRefreshToken(anyString(), eq(userId));
	}

	@Test
	void testRefresh_InvalidToken() {
		// Given
		String refreshToken = "invalidRefreshToken";
		when(jwtProvider.validateToken(refreshToken)).thenReturn(false);

		UserRequestDto.Refresh refreshRequest = new UserRequestDto.Refresh(refreshToken);

		// When & Then
		assertThatThrownBy(() -> userService.refresh(refreshRequest))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("Refresh Token이 만료되었거나 정상적인 Token이 아닙니다.");
		verify(refreshTokenRepository, never()).deleteRefreshToken(anyString());
		verify(refreshTokenRepository, never()).saveRefreshToken(anyString(), anyString());
	}

	@Test
	void testRefreshTokenDeletion_WhenUserIdIsNull() {
		// Given
		String refreshToken = "validRefreshToken";
		String userId = null; // userId가 null
		UserRequestDto.Refresh userRequestDto = UserRequestDto.Refresh.builder().refreshToken(refreshToken).build();

		// Mock repository interactions
		when(jwtProvider.validateToken(refreshToken)).thenReturn(true); // validateToken이 true를 반환하도록 설정
		when(refreshTokenRepository.getRefreshToken(refreshToken)).thenReturn(userId);

		// When
		userService.refresh(userRequestDto);

		// Then
		verify(refreshTokenRepository, never()).deleteRefreshToken(anyString()); // refreshToken이 삭제되지 않아야 함
	}

	@Test
	void checkUpdatePermission() {
		// given
		User mockUser = new User();
		mockUser.setPermission((short)1); // initial permission
		short newPermission = 3;
		when(userRepository.save(any(User.class))).thenReturn(mockUser);

		// when
		User updatedUser = userService.addSurveyPermission(mockUser);

		// then
		assertThat(updatedUser.getPermission()).isEqualTo(newPermission);
		verify(userRepository).save(mockUser);
	}

	@Test
	void testSendVerificationCode_Success() {
		// Given
		String userTel = "01012341234";
		when(telCodeValidationRepository.hasKey(userTel)).thenReturn(false);
		when(telCodeValidationRepository.incrementDailyRequestCount(userTel)).thenReturn(true);

		// When
		assertDoesNotThrow(() -> userService.sendVerificationCode(userTel));

		// Then
		verify(smsService).sendOne(eq(userTel), anyString());
		verify(telCodeValidationRepository).createSmsCertification(eq(userTel), anyString());
	}

	@Test
	void testSendVerificationCode_AlreadyExists() {
		// Given
		String userTel = "01012341234";
		when(userRepository.existsUserByTel(userTel)).thenReturn(true);

		// When & Then
		assertThatThrownBy(() -> userService.sendVerificationCode(userTel))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_EXISTS);
	}

	@Test
	void testSendVerificationCode_AlreadySent() {
		// Given
		String userTel = "01012341234";
		when(telCodeValidationRepository.hasKey(userTel)).thenReturn(true);

		// When & Then
		assertThatThrownBy(() -> userService.sendVerificationCode(userTel))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_ALREADY_SENT);
	}

	@Test
	void testSendVerificationCode_DailyLimitExceeded() {
		// Given
		String userTel = "01012341234";
		when(telCodeValidationRepository.hasKey(userTel)).thenReturn(false);
		when(telCodeValidationRepository.incrementDailyRequestCount(userTel)).thenReturn(false);

		// When & Then
		assertThatThrownBy(() -> userService.sendVerificationCode(userTel))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DAILY_LIMIT_EXCEEDED);
	}

	@Test
	void testSendVerificationCode_RedisConnectionFailure() {
		// Given
		String userTel = "01012341234";
		when(telCodeValidationRepository.hasKey(userTel)).thenReturn(false);
		when(telCodeValidationRepository.incrementDailyRequestCount(userTel)).thenReturn(true);
		doThrow(new RedisConnectionFailureException("Redis connection failed"))
			.when(telCodeValidationRepository).createSmsCertification(eq(userTel), anyString());

		// When & Then
		assertThatThrownBy(() -> userService.sendVerificationCode(userTel))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.REDIS_CONNECTION_FAILURE);
	}

	@Test
	void testSendVerificationCode_SmsFailure() {
		// Arrange
		String userTel = "01012341234";
		when(telCodeValidationRepository.hasKey(userTel)).thenReturn(false);
		when(telCodeValidationRepository.incrementDailyRequestCount(userTel)).thenReturn(true);
		doThrow(new RuntimeException("SMS sending failed")).when(smsService).sendOne(eq(userTel), anyString());

		// Act & Assert
		assertThatThrownBy(() -> userService.sendVerificationCode(userTel))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_FAILURE);
	}

	@Test
	void testValidateVerificationCode_Success() {
		// Given
		UserRequestDto.Validation request = new UserRequestDto.Validation("01012341234", "123456");
		when(telCodeValidationRepository.getSmsCertification(request.getTel())).thenReturn("123456");

		// When
		assertDoesNotThrow(() -> userService.validateVerificationCode(request));

		// Then
		verify(telCodeValidationRepository).removeSmsCertification(request.getTel());
	}

	@Test
	void testValidateVerificationCode_CodeNotFound() {
		// Given
		UserRequestDto.Validation request = new UserRequestDto.Validation("01012341234", "123456");
		when(telCodeValidationRepository.getSmsCertification(request.getTel())).thenReturn(null);

		// When & Then
		assertThatThrownBy(() -> userService.validateVerificationCode(request))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
	}

	@Test
	void testValidateVerificationCode_CodeMismatch() {
		// Given
		UserRequestDto.Validation request = new UserRequestDto.Validation("01012341234", "123456");
		when(telCodeValidationRepository.getSmsCertification(request.getTel())).thenReturn("654321");

		// When & Then
		assertThatThrownBy(() -> userService.validateVerificationCode(request))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_VALIDATION_FAILURE);
	}

	@Test
	void testValidateVerificationCode_RedisConnectionFailure() {
		// Given
		UserRequestDto.Validation request = new UserRequestDto.Validation("01012341234", "123456");
		when(telCodeValidationRepository.getSmsCertification(request.getTel()))
			.thenThrow(new RedisConnectionFailureException("Redis connection failed"));

		// When & Then
		assertThatThrownBy(() -> userService.validateVerificationCode(request))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.REDIS_CONNECTION_FAILURE);
	}

	@Test
	void testValidateVerificationCode_UnexpectedError() {
		// Given
		UserRequestDto.Validation request = new UserRequestDto.Validation("01012341234", "123456");
		when(telCodeValidationRepository.getSmsCertification(request.getTel()))
			.thenThrow(new RuntimeException("Unexpected error"));

		// When & Then
		assertThatThrownBy(() -> userService.validateVerificationCode(request))
			.isInstanceOf(CustomException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_FAILURE);
	}

	@Test
	void testGetUserInfo_UserExists() {
		// given
		String userId = "user123";
		User mockUser = new User();
		mockUser.setId(userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

		// when
		UserResponseDto.UserInfo result = userService.getUserInfo(userId);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(userId);
		verify(userRepository).findById(userId);
	}

	@Test
	void testGetUserInfo_UserNotFound() {
		// given
		String userId = "user123";
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userService.getUserInfo(userId))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("해당 유저를 찾을 수 없습니다");
		verify(userRepository).findById(userId);
	}

	@Test
	void testUpdateUserInfo_Success() {
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder()
			.password("newPassword")
			.profileImageUrl("http://example.com/image.jpg")
			.nickname("newNickname")
			.build();

		User mockUser = new User();
		mockUser.setId(userId);
		mockUser.setPassword("oldPassword");

		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
		when(passwordEncoder.encode(updateUserRequest.getPassword())).thenReturn("hashedNewPassword");

		// when
		UserResponseDto.UserInfo result = userService.updateUserInfo(userId, updateUserRequest);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPassword()).isEqualTo("hashedNewPassword");
		assertThat(mockUser.getProfileImageUrl()).isEqualTo("http://example.com/image.jpg");
		assertThat(mockUser.getNickname()).isEqualTo("newNickname");

		verify(userRepository).save(mockUser);
	}

	@Test
	void testUpdateUserInfo_UserNotFound() {
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder()
			.password("newPassword")
			.build();

		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userService.updateUserInfo(userId, updateUserRequest))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("유저를 찾을 수 없습니다.");

		verify(userRepository).findById(userId);
	}

	@Test
	void testUpdateUserInfo_OnlyPassword() {
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder()
			.password("newPassword")
			.build();

		User mockUser = new User();
		mockUser.setId(userId);
		mockUser.setPassword("oldPassword");
		mockUser.setProfileImageUrl("oldImageUrl");
		mockUser.setNickname("oldNickname");

		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
		when(passwordEncoder.encode("newPassword")).thenReturn("hashedNewPassword");

		// when
		UserResponseDto.UserInfo result = userService.updateUserInfo(userId, updateUserRequest);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPassword()).isEqualTo("hashedNewPassword");
		assertThat(mockUser.getProfileImageUrl()).isEqualTo("oldImageUrl");
		assertThat(mockUser.getNickname()).isEqualTo("oldNickname");

		verify(userRepository).save(mockUser);
		verify(passwordEncoder).encode("newPassword");
	}

	@Test
	void testUpdateUserInfo_OnlyProfileImageUrl() {
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder()
			.profileImageUrl("newImageUrl")
			.build();

		User mockUser = new User();
		mockUser.setId(userId);
		mockUser.setPassword("oldPassword");
		mockUser.setProfileImageUrl("oldImageUrl");
		mockUser.setNickname("oldNickname");

		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

		// when
		UserResponseDto.UserInfo result = userService.updateUserInfo(userId, updateUserRequest);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPassword()).isEqualTo("oldPassword");
		assertThat(mockUser.getProfileImageUrl()).isEqualTo("newImageUrl");
		assertThat(mockUser.getNickname()).isEqualTo("oldNickname");

		verify(userRepository).save(mockUser);
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void testUpdateUserInfo_OnlyNickname() {
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder()
			.nickname("newNickname")
			.build();

		User mockUser = new User();
		mockUser.setId(userId);
		mockUser.setPassword("oldPassword");
		mockUser.setProfileImageUrl("oldImageUrl");
		mockUser.setNickname("oldNickname");

		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

		// when
		UserResponseDto.UserInfo result = userService.updateUserInfo(userId, updateUserRequest);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPassword()).isEqualTo("oldPassword");
		assertThat(mockUser.getProfileImageUrl()).isEqualTo("oldImageUrl");
		assertThat(mockUser.getNickname()).isEqualTo("newNickname");

		verify(userRepository).save(mockUser);
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void testUpdateUserInfo_AllFieldsNull() {
		// 이 부분은 controller 단에서 사전에 차단됨
		// given
		String userId = "user123";
		UserRequestDto.UpdateUser updateUserRequest = UserRequestDto.UpdateUser.builder().build();

		User mockUser = new User();
		mockUser.setId(userId);
		mockUser.setPassword("oldPassword");
		mockUser.setProfileImageUrl("oldImageUrl");
		mockUser.setNickname("oldNickname");

		when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

		// when
		UserResponseDto.UserInfo result = userService.updateUserInfo(userId, updateUserRequest);

		// then
		assertThat(result).isNotNull();
		assertThat(mockUser.getPassword()).isEqualTo("oldPassword");
		assertThat(mockUser.getProfileImageUrl()).isEqualTo("oldImageUrl");
		assertThat(mockUser.getNickname()).isEqualTo("oldNickname");

		verify(userRepository).save(mockUser);
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void testDeleteUserInfo_UserExists() {
		// given
		String userId = "user123";
		User mockUser = new User();
		mockUser.setId(userId);
		when(userRepository.existsById(eq(userId))).thenReturn(true);

		// when
		userService.deleteUser(userId);

		// then
		verify(userRepository).deleteById(mockUser.getId());
	}

	@Test
	void testDeleteUserInfo_UserNotFound() {
		// given
		String userId = "user123";
		when(userRepository.existsById(userId)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> userService.deleteUser(userId))
			.isInstanceOf(CustomException.class)
			.hasMessageContaining("해당 유저를 찾을 수 없습니다");
	}
}
