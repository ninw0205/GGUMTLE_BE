package com.hana4.ggumtle.dto.post;

import java.io.Serializable;

import com.hana4.ggumtle.dto.BaseDto;
import com.hana4.ggumtle.dto.user.UserResponseDto;
import com.hana4.ggumtle.model.entity.group.GroupCategory;
import com.hana4.ggumtle.model.entity.post.Post;
import com.hana4.ggumtle.model.entity.post.PostType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Generated
@Schema(description = "게시물 응답 DTO")
public class PostResponseDto {

	@Schema(name = "PostInfoResponse", description = "게시물 상세 정보 응답 DTO")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@ToString
	@SuperBuilder
	public static class PostInfo extends BaseDto implements Serializable {
		@Schema(description = "게시물 ID", example = "1234")
		private Long id;

		@Schema(description = "사용자 ID", example = "user123")
		private String userId;

		@Schema(description = "그룹 ID", example = "5678")
		private Long groupId;

		@Schema(description = "스냅샷", example = "2023년 6월 15일의 스냅샷")
		private String snapShot;

		@Schema(description = "이미지 URL들", example = "http://example.com/image1.jpg,http://example.com/image2.jpg")
		private String imageUrls;

		@Schema(description = "게시물 내용", example = "오늘은 좋은 날씨입니다.")
		private String content;

		@Schema(description = "게시물 타입", example = "POST")
		private PostType postType;

		@Schema(description = "사용자 간단 정보")
		private UserResponseDto.BriefInfo userBriefInfo;

		@Schema(description = "그룹 카테고리", example = "STUDY")
		private GroupCategory groupCategory;

		@Schema(description = "좋아요 여부", example = "true")
		private boolean isLiked;

		@Schema(description = "본인 게시물 여부", example = "false")
		private boolean isMine;

		@Schema(description = "좋아요 수", example = "10")
		private int likeCount;

		@Schema(description = "댓글 수", example = "5")
		private int commentCount;

		public static PostInfo from(Post post, boolean isLiked, boolean isMine, int likeCount, int commentCount) {
			return PostInfo.builder()
				.id(post.getId())
				.userId(post.getUser().getId())
				.groupId(post.getGroup().getId())
				.snapShot(post.getSnapshot())
				.imageUrls(post.getImageUrls())
				.content(post.getContent())
				.postType(post.getPostType())
				.userBriefInfo(UserResponseDto.BriefInfo.from(post.getUser()))
				.likeCount(likeCount)
				.commentCount(commentCount)
				.isLiked(isLiked)
				.isMine(isMine)
				.createdAt(post.getCreatedAt())
				.updatedAt(post.getUpdatedAt())
				.build();
		}
	}

	@Schema(name = "ShareInfoResponse", description = "게시물 공유 정보 응답 DTO")
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor
	@ToString
	@SuperBuilder
	public static class ShareInfo extends BaseDto {
		@Schema(description = "게시물 ID", example = "1234")
		private Long id;

		@Schema(description = "게시물 내용", example = "이 뉴스를 공유합니다.")
		private String content;

		@Schema(description = "사용자 간단 정보")
		private UserResponseDto.BriefInfo briefInfo;

		@Schema(description = "게시물 타입", example = "NEWS")
		private PostType postType;

		public static ShareInfo from(Post post) {
			return ShareInfo.builder()
				.id(post.getId())
				.content(post.getContent())
				.briefInfo(UserResponseDto.BriefInfo.from(post.getUser()))
				.postType(post.getPostType())
				.createdAt(post.getCreatedAt())
				.build();
		}
	}
}
