package com.hana4.ggumtle.dto.user;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.hana4.ggumtle.model.entity.user.User;
import com.hana4.ggumtle.model.entity.user.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "사용자 응답 DTO")
@Generated
public class UserResponseDto {
	@Schema(description = "사용자 정보 응답")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@Builder
	public static class UserInfo {
		@Schema(description = "사용자 ID", example = "user123")
		private String id;

		@Schema(description = "전화번호", example = "01012345678")
		private String tel;

		@Schema(description = "이름", example = "홍길동")
		private String name;

		@Schema(description = "권한 레벨", example = "1")
		private short permission;

		@Schema(description = "생년월일", example = "1990-01-01T00:00:00")
		private LocalDateTime birthDate;

		@Schema(description = "성별", example = "m")
		private String gender;

		@Schema(description = "사용자 역할", example = "USER")
		private UserRole role;

		@Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
		private String profileImageUrl;

		@Schema(description = "닉네임", example = "익명의고라니")
		private String nickname;

		public static UserInfo from(User user) {
			return UserInfo.builder()
				.id(user.getId())
				.tel(user.getTel())
				.name(user.getName())
				.permission(user.getPermission())
				.birthDate(user.getBirthDate())
				.gender(user.getGender())
				.role(user.getRole())
				.profileImageUrl(user.getProfileImageUrl())
				.nickname(user.getNickname())
				.build();
		}
	}

	@Schema(description = "로그인 응답")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@Builder
	public static class TokensWithPermission {
		@Schema(description = "accessToken", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
		private String accessToken;
		@Schema(description = "refreshToken", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
		private String refreshToken;
		@Schema(description = "permission", example = "0")
		private short permission;
	}

	@Schema(description = "토큰 갱신 응답")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@Builder
	public static class Tokens {
		@Schema(description = "new accessToken", example = "2eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
		private String accessToken;
		@Schema(description = "new refreshToken", example = "3eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
		private String refreshToken;
	}

	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@Builder
	public static class BriefInfo implements Serializable {
		private String name;
		private String profileImageUrl;
		private String nickname;

		public static BriefInfo from(User user) {
			return BriefInfo.builder()
				.name(user.getName())
				.profileImageUrl(user.getProfileImageUrl())
				.nickname(user.getNickname())
				.build();
		}
	}
}
