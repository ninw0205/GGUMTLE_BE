package com.hana4.ggumtle.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hana4.ggumtle.dto.CustomApiResponse;
import com.hana4.ggumtle.dto.advertisement.AdvertisementResponseDto;
import com.hana4.ggumtle.dto.post.PostLikeResponseDto;
import com.hana4.ggumtle.dto.post.PostRequestDto;
import com.hana4.ggumtle.dto.post.PostResponseDto;
import com.hana4.ggumtle.global.error.CustomException;
import com.hana4.ggumtle.global.error.ErrorCode;
import com.hana4.ggumtle.model.entity.group.GroupCategory;
import com.hana4.ggumtle.security.CustomUserDetails;
import com.hana4.ggumtle.service.AdvertisementService;
import com.hana4.ggumtle.service.PostService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community")
@Tag(name = "Post", description = "게시물 관련 API")
public class PostController {
	private final PostService postService;
	private final AdvertisementService advertisementService;

	@Operation(summary = "게시물 작성", description = "새로운 게시물을 작성합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "게시물 작성 성공"),
		@ApiResponse(responseCode = "400", description = "게시물 작성 실패(Snapshot 데이터 문제)",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 400, \"error\": \"Bad Request\", \"message\": \"snapshot 데이터에 문제가 있습니다.\" }"
			))),
		@ApiResponse(responseCode = "404", description = "게시물 작성 실패(그룹을 찾을 수 없음)",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹이 존재하지 않습니다.\" }"
			)))
	})
	@PostMapping("/group/{groupId}/post")
	public ResponseEntity<CustomApiResponse<PostResponseDto.PostInfo>> writePost(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "게시물 내용") @RequestBody @Valid PostRequestDto.Write write,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) throws
		JsonProcessingException {
		return ResponseEntity.ok(
			CustomApiResponse.success(postService.save(groupId, write, customUserDetails.getUser())));
	}

	@Operation(summary = "게시물 상세 조회", description = "특정 게시물을 조회합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "게시물 조회 성공"),
		@ApiResponse(responseCode = "404", description = "게시물 조회 실패",
			content = @Content(mediaType = "application/json", examples = {
				@ExampleObject(name = "글 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 글이 존재하지 않습니다.\" }"),
				@ExampleObject(name = "그룹 내 글 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"글이 해당 그룹에 있지 않습니다.\" }")
			}))
	})
	@GetMapping("/group/{groupId}/post/{postId}")
	public ResponseEntity<CustomApiResponse<PostResponseDto.PostInfo>> getPost(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "게시물 ID") @PathVariable Long postId,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {

		return ResponseEntity.ok(
			CustomApiResponse.success(postService.getPost(groupId, postId, customUserDetails.getUser())));
	}

	@Operation(summary = "게시물 목록 조회", description = "페이지별로 게시물 목록을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "게시물 목록 조회 성공")
	@GetMapping("/group/{groupId}/post")
	public ResponseEntity<CustomApiResponse<Page<PostResponseDto.PostInfo>>> getPostsByPage(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int offset,
		@Parameter(description = "페이지 번호") @RequestParam(defaultValue = "10") int limit,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
		if (limit <= 0) {
			throw new CustomException(ErrorCode.INVALID_PARAMETER, "limit은 0 이상이어야 합니다.");
		}
		Pageable pageable = PageRequest.of(offset / limit, limit);
		return ResponseEntity.ok(
			CustomApiResponse.success(postService.getPostsByPage(groupId, pageable, customUserDetails.getUser())));
	}

	@Operation(summary = "게시물 목록 조회(인기순)", description = "페이지 별로 게시물 목록을 인기순으로 조회합니다.")
	@ApiResponse(responseCode = "200", description = "게시물 목록 조회 성공")
	@GetMapping("/post/popular")
	public ResponseEntity<CustomApiResponse<Page<PostResponseDto.PostInfo>>> getPopularPostsByPage(
		@Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int offset,
		@Parameter(description = "페이지 번호") @RequestParam(defaultValue = "10") int limit,
		@Parameter(description = "그룹 카테고리") @RequestParam(required = false) GroupCategory category,
		@Parameter(description = "검색") @RequestParam(required = false) String search,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
		if (limit <= 0) {
			throw new CustomException(ErrorCode.INVALID_PARAMETER, "limit은 0 이상이어야 합니다.");
		}
		
		Pageable pageable = PageRequest.of(offset / limit, limit);
		return ResponseEntity.ok(
			CustomApiResponse.success(
				postService.getPopularPostsByPage(pageable, customUserDetails.getUser(), category, search)));
	}

	@Operation(summary = "게시물 수정", description = "특정 게시물을 수정합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "게시물 수정 성공"),
		@ApiResponse(responseCode = "401", description = "게시물 수정 실패",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 401, \"error\": \"Unauthorized\", \"message\": \"해당 글에 권한이 없습니다.\" }"
			))),
		@ApiResponse(responseCode = "404", description = "게시물 수정 실패",
			content = @Content(mediaType = "application/json", examples = {
				@ExampleObject(name = "", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 글이 존재하지 않습니다.\" }"),
				@ExampleObject(name = "", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹에 권한이 없습니다.\" }")
			}))
	})
	@PatchMapping("/group/{groupId}/post/{postId}")
	public ResponseEntity<CustomApiResponse<PostResponseDto.PostInfo>> updatePost(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "게시물 ID") @PathVariable Long postId,
		@Parameter(description = "수정할 게시물 내용") @RequestBody @Valid PostRequestDto.Write write,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) throws
		JsonProcessingException {

		return ResponseEntity.ok(
			CustomApiResponse.success(postService.updatePost(groupId, postId, write, customUserDetails.getUser())));
	}

	@Operation(summary = "게시물 삭제", description = "특정 게시물을 삭제합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "게시물 삭제 성공"),
		@ApiResponse(responseCode = "401", description = "게시물 삭제 실패",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 401, \"error\": \"Unauthorized\", \"message\": \"해당 글에 권한이 없습니다.\" }"
			))),
		@ApiResponse(responseCode = "404", description = "게시물 삭제 실패",
			content = @Content(mediaType = "application/json", examples = {
				@ExampleObject(name = "글 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 글이 존재하지 않습니다.\" }"),
				@ExampleObject(name = "그룹 권한 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹에 권한이 없습니다.\" }")
			}))
	})
	@DeleteMapping("/group/{groupId}/post/{postId}")
	public ResponseEntity<CustomApiResponse<PostResponseDto.PostInfo>> deletePost(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "게시물 ID") @PathVariable Long postId,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {

		postService.deletePost(groupId, postId, customUserDetails.getUser());
		return ResponseEntity.ok(CustomApiResponse.success());
	}

	@Operation(summary = "게시물 좋아요 추가", description = "특정 게시물에 좋아요를 추가합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "좋아요 추가 성공"),
		@ApiResponse(responseCode = "404", description = "그룹 권한 없음",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹에 권한이 없습니다.\" }"
			)))
	})
	@PostMapping("/group/{groupId}/post/{postId}/like")
	public ResponseEntity<CustomApiResponse<Boolean>> likePost(@PathVariable Long groupId,
		@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails customUserDetails) {
		return ResponseEntity.ok(
			CustomApiResponse.success(postService.addLike(groupId, postId, customUserDetails.getUser())));
	}

	@Operation(summary = "게시물 좋아요 취소", description = "특정 게시물의 좋아요를 취소합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "좋아요 취소 성공"),
		@ApiResponse(responseCode = "404", description = "게시물 삭제 실패",
			content = @Content(mediaType = "application/json", examples = {
				@ExampleObject(name = "글 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 글이 존재하지 않습니다.\" }"),
				@ExampleObject(name = "그룹 권한 없음", value = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹에 권한이 없습니다.\" }")
			}))
	})
	@DeleteMapping("/group/{groupId}/post/{postId}/dislike")
	public ResponseEntity<CustomApiResponse<Boolean>> dislikePost(@PathVariable Long groupId,
		@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails customUserDetails) {
		return ResponseEntity.ok(CustomApiResponse.success(postService.removeLike(groupId, postId, customUserDetails.getUser())));
	}

	@Operation(summary = "새소식 공유", description = "새 소식(버켓리스트 완료)을 공유합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "새소식 공유 성공"),
		@ApiResponse(responseCode = "401", description = "새소식 공유 실패",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 401, \"error\": \"Unauthorized\", \"message\": \"해당 그룹에 권한이 없습니다.\" }"
			))),
		@ApiResponse(responseCode = "404", description = "새소식 공유 실패",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹이 존재하지 않습니다.\" }"
			)))
	})
	@PostMapping("/group/{groupId}/post/share")
	public ResponseEntity<CustomApiResponse<PostResponseDto.ShareInfo>> shareBucketNews(
		@Parameter(description = "그룹 ID") @PathVariable Long groupId,
		@Parameter(description = "새소식 내용") @RequestBody @Valid PostRequestDto.Share share,
		@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
		return ResponseEntity.ok(
			CustomApiResponse.success(postService.saveNews(groupId, share, customUserDetails.getUser())));
	}

	@Operation(summary = "그룹별 광고 조회", description = "특정 그룹의 광고를 조회합니다.")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "광고 조회 성공"),
		@ApiResponse(responseCode = "404", description = "광고 조회 실패",
			content = @Content(mediaType = "application/json", schema = @Schema(
				example = "{ \"code\": 404, \"error\": \"Not Found\", \"message\": \"해당 그룹이 존재하지 않습니다.\" }"
			)))
	})
	@GetMapping("/group/{groupId}/advertisement")
	public ResponseEntity<CustomApiResponse<AdvertisementResponseDto.CommunityAd>> getCommunityAd(
		@PathVariable Long groupId) {
		return ResponseEntity.ok(
			CustomApiResponse.success(advertisementService.getCommunityAd(groupId)));
	}
}
