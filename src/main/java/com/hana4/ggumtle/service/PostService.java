package com.hana4.ggumtle.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana4.ggumtle.dto.bucketList.BucketResponseDto;
import com.hana4.ggumtle.dto.post.PostLikeResponseDto;
import com.hana4.ggumtle.dto.post.PostRequestDto;
import com.hana4.ggumtle.dto.post.PostResponseDto;
import com.hana4.ggumtle.global.error.CustomException;
import com.hana4.ggumtle.global.error.ErrorCode;
import com.hana4.ggumtle.model.entity.group.Group;
import com.hana4.ggumtle.model.entity.group.GroupCategory;
import com.hana4.ggumtle.model.entity.post.Post;
import com.hana4.ggumtle.model.entity.postLike.PostLike;
import com.hana4.ggumtle.model.entity.user.User;
import com.hana4.ggumtle.repository.PostLikeRepository;
import com.hana4.ggumtle.repository.PostRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {
	private final PostRepository postRepository;
	private final PostLikeRepository postLikeRepository;
	private final GroupService groupService;
	private final CommentService commentService;
	private final BucketService bucketService;
	private final GoalPortfolioService goalPortfolioService;
	private final MyDataService myDataService;
	private final ObjectMapper objectMapper;
	private final RedisService redisService;

	private boolean checkUserWithPost(User user, Post post) {
		return !user.getId().equals(post.getUser().getId());
	}

	private boolean checkUserWithGroup(Long groupId, User user) {
		Group group = groupService.getGroup(groupId);
		return groupService.isMemberOfGroup(groupId, user.getId());
	}

	private String makeSnapShot(PostRequestDto.Write postRequestDto, User user) throws
		JsonProcessingException {
		Map<String, Object> snapShotResponse = new HashMap<>();
		List<BucketResponseDto.BucketInfo> bucketList = new ArrayList<>();

		Map<String, Object> snapShot = objectMapper.readValue(postRequestDto.getSnapShot(),
			new TypeReference<>() {
			});

		List<Integer> bucketIds = objectMapper.convertValue(snapShot.get("bucketId"),
			new TypeReference<>() {
			});

		Boolean portfolio = objectMapper.convertValue(snapShot.get("portfolio"), new TypeReference<>() {
		});

		if (bucketIds == null || portfolio == null) {
			throw new CustomException(ErrorCode.INVALID_PARAMETER, "snapshot 데이터에 문제가 있습니다.");
		}

		for (int bucketId : bucketIds) {
			bucketList.add(BucketResponseDto.BucketInfo.from(bucketService.getBucket((long)bucketId)));
		}

		snapShotResponse.put("bucketLists", bucketList);

		if (portfolio) {
			snapShotResponse.put("goalPortfolio", goalPortfolioService.getGoalPortfolioByUserId(user.getId()));
			snapShotResponse.put("currentPortfolio", myDataService.getMyDataRateByUserId(user.getId()));
		}

		return objectMapper.writeValueAsString(snapShotResponse);
	}

	public PostResponseDto.PostInfo save(Long groupId, PostRequestDto.Write postRequestDto, User user) throws
		JsonProcessingException {
		Group group = groupService.getGroup(groupId);

		postRequestDto.setSnapShot(makeSnapShot(postRequestDto, user));
		return PostResponseDto.PostInfo.from(postRepository.save(postRequestDto.toEntity(user, group)), false, true, 0,
			0);
	}

	public PostResponseDto.PostInfo getPost(Long groupId, Long postId, User user) {
		Post post = getPostById(postId);
		if (!post.getGroup().getId().equals(groupId)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "글이 해당 그룹에 있지 않습니다.");
		}
		return PostResponseDto.PostInfo.from(post, isAuthorLike(postId, user.getId()),
			post.getUser().getId().equals(user.getId()), countLikeByPostId(postId),
			commentService.countCommentByPostId(postId));
	}

	public Page<PostResponseDto.PostInfo> getPostsByPage(Long groupId, Pageable pageable, User user) {
		return postRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId, pageable)
			.map(post -> {
				boolean isLiked = isAuthorLike(post.getId(), user.getId());
				boolean isMine = post.getUser().getId().equals(user.getId());
				return PostResponseDto.PostInfo.from(post, isLiked, isMine, countLikeByPostId(post.getId()),
					commentService.countCommentByPostId(post.getId()));
			});
	}

	// Page<Post> 대신 List<Post>와 Total Count를 캐싱하도록 로직 변경
	public Page<PostResponseDto.PostInfo> getPopularPostsByPage(Pageable pageable, User user,
		GroupCategory groupCategory, String search) {

		// Redis 키 생성
		String contentKey = "popularPosts:content:" + buildContentCacheKey(pageable, groupCategory, search);
		String totalCountKey = "popularPosts:totalCount:" + buildTotalCountCacheKey(groupCategory, search);

		// 캐시에서 Content List 및 Total Count 조회
		List<Post> cachedContent = redisService.getValuesFromKey(contentKey);
		Object cachedTotalObject = redisService.getValuesFromKey(totalCountKey);

		// Total Count가 Long 타입이 아닌 경우를 대비
		Long totalCount = (cachedTotalObject instanceof Long) ? (Long) cachedTotalObject : 0;
		System.out.println(cachedContent + " " + totalCount);

		if (cachedContent != null) {
			// 캐시 히트: PageImpl 수동 생성 후 DTO 변환
			Page<Post> cachedPage = new PageImpl<>(cachedContent, pageable, totalCount);
			return convertPageToDto(cachedPage, user);
		}

		// 캐시 미스: DB에서 조회
		Page<Post> postPage = findPostsFromRepository(pageable, groupCategory, search);

		// Redis에 저장
		List<Post> contentToCache = postPage.getContent();
		System.out.println(postPage.getContent());
		long totalCountToCache = postPage.getTotalElements(); // long을 캐싱해도 Redis에서 Long으로 복원됩니다.

		redisService.setKeyAndValue(contentKey, contentToCache, Duration.ofMinutes(5));
		redisService.setKeyAndValue(totalCountKey, totalCountToCache, Duration.ofMinutes(5));

		// DTO 변환 및 반환
		return convertPageToDto(postPage, user);
	}

	private String buildContentCacheKey(Pageable pageable, GroupCategory groupCategory, String search) {
		return String.format("%d:%d:%s:%s:%s",
			pageable.getPageNumber(),
			pageable.getPageSize(),
			pageable.getSort().toString(),
			groupCategory != null ? groupCategory.name() : "none",
			search != null ? search : "none");
	}

	private String buildTotalCountCacheKey(GroupCategory groupCategory, String search) {
		return String.format("%s:%s",
			groupCategory != null ? groupCategory.name() : "none",
			search != null ? search : "none");
	}

	// DB 조회 로직 (기존 Service 로직에서 분리)
	private Page<Post> findPostsFromRepository(Pageable pageable, GroupCategory groupCategory, String search) {
			if (groupCategory != null && search != null) {
				return postRepository.findAllPostsGroupedByGroupCategoryWithSearchParam(pageable, groupCategory, search);
			}

			if (groupCategory != null) {
				return postRepository.findAllPostsGroupedByGroupCategory(pageable, groupCategory);
			}

			if (search != null) {
				return postRepository.findAllPostsWithSearchParam(pageable, search);
			}

			return postRepository.findAllPostsWithLikeCount(pageable);
	}

	// 사용자별 정보 추가 로직 (재사용을 위해 별도 메서드로 분리)
	private Page<PostResponseDto.PostInfo> convertPageToDto(Page<Post> postPage, User user) {
		return postPage.map(post -> {
			boolean isLiked = isAuthorLike(post.getId(), user.getId());
			boolean isMine = post.getUser().getId().equals(user.getId());
			return PostResponseDto.PostInfo.from(post, isLiked, isMine, countLikeByPostId(post.getId()),
				commentService.countCommentByPostId(post.getId()));
		});
	}

	public PostResponseDto.PostInfo updatePost(Long groupId, Long postId, PostRequestDto.Write postRequestDto,
		User user) throws JsonProcessingException {
		if (!checkUserWithGroup(groupId, user)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 그룹에 권한이 없습니다.");
		}

		Post post = getPostById(postId);
		if (checkUserWithPost(user, post)) {
			throw new CustomException(ErrorCode.ACCESS_DENIED, "해당 글에 권한이 없습니다.");
		}

		post.setImageUrls(postRequestDto.getImageUrls());
		post.setContent(postRequestDto.getContent());
		post.setSnapshot(makeSnapShot(postRequestDto, user));

		Post savedPost = postRepository.save(post);
		redisService.deleteKeysByPrefix("popularPosts:");

		return PostResponseDto.PostInfo.from(savedPost, isAuthorLike(post.getId(), user.getId()), true,
			countLikeByPostId(postId), commentService.countCommentByPostId(postId));
	}

	public void deletePost(Long groupId, Long postId, User user) {
		if (!checkUserWithGroup(groupId, user)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 그룹에 권한이 없습니다.");
		}

		Post post = getPostById(postId);

		if (checkUserWithPost(user, post)) {
			throw new CustomException(ErrorCode.ACCESS_DENIED, "해당 글에 권한이 없습니다.");
		}

		postRepository.deleteById(postId);
		redisService.deleteKeysByPrefix("popularPosts:");
	}

	public Post getPostById(Long postId) {
		return postRepository.findById(postId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND, "해당 글이 존재하지 않습니다."));
	}

	public boolean isAuthorLike(Long postId, String userId) {
		return postLikeRepository.findByPostIdAndUserId(postId, userId).isPresent();
	}

	public int countLikeByPostId(Long postId) {
		return postLikeRepository.countByPostId(postId);
	}

	public PostLikeResponseDto.Add addLike(Long groupId, Long postId, User user) {
		if (!checkUserWithGroup(groupId, user)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 그룹에 권한이 없습니다.");
		}

		return PostLikeResponseDto.Add.from(
			postLikeRepository.save(PostLike.builder().user(user).post(getPostById(postId)).build()));
	}

	public void removeLike(Long groupId, Long postId, User user) {
		if (!checkUserWithGroup(groupId, user)) {
			throw new CustomException(ErrorCode.NOT_FOUND, "해당 그룹에 권한이 없습니다.");
		}
		Post post = getPostById(postId);

		postLikeRepository.findByPostIdAndUserId(postId, user.getId()).ifPresent(postLikeRepository::delete);
	}

	public PostResponseDto.ShareInfo saveNews(Long groupId, PostRequestDto.Share share, User user) {
		if (!checkUserWithGroup(groupId, user)) {
			throw new CustomException(ErrorCode.ACCESS_DENIED, "해당 그룹에 권한이 없습니다.");
		}
		Group group = groupService.getGroup(groupId);
		return PostResponseDto.ShareInfo.from(postRepository.save(share.toEntity(user, group)));
	}
}