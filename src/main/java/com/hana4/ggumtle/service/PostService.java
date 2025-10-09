package com.hana4.ggumtle.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hana4.ggumtle.dto.CacheWrapper;
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

import jakarta.annotation.PostConstruct;
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
	@Qualifier("caffeineCacheManager")
	private final CaffeineCacheManager caffeineCacheManager;
	@Qualifier("redisCacheManager")
	private final RedisCacheManager redisCacheManager;

	private Cache caffeineCacheData;
	private Cache redisCacheData;

	@PostConstruct
	public void initCaches() {
		this.caffeineCacheData	 = caffeineCacheManager.getCache("popularPostsCaffeine");
		this.redisCacheData = redisCacheManager.getCache("popularPostsRedis");
	}

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
		String cacheKey = "popularPosts";

		Long globalVersion = redisCacheData.get("version", Long.class);
		CacheWrapper<Page<Post>> localData = caffeineCacheData.get(cacheKey, CacheWrapper.class);

		if (localData != null && globalVersion != null && localData.getVersion() >= globalVersion) {
			return convertPageToDto(localData.getData(), user);
		} else {
			if (redisCacheData.get(cacheKey) != null) {
				CacheWrapper<Page<Post>> redisPostPage = redisCacheData.get(cacheKey, CacheWrapper.class);
				if (redisPostPage != null) {
					caffeineCacheData.put(cacheKey, new CacheWrapper<>(redisPostPage.getData(), globalVersion));
				}
				return convertPageToDto(redisPostPage.getData(), user);
			} else {
				Page<Post> dbPostPage = findPostsFromRepository(pageable, groupCategory, search);

				Long newVersion = System.currentTimeMillis();
				redisCacheData.put("version", newVersion);
				redisCacheData.put(cacheKey, new CacheWrapper<>(dbPostPage, newVersion));
				caffeineCacheData.put(cacheKey, new CacheWrapper<>(dbPostPage, newVersion));

				return convertPageToDto(dbPostPage, user);
			}
		}
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
		caffeineCacheData.evict("popularPosts");
		redisCacheData.evict("version");
		redisCacheData.evict("popularPosts");

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
		caffeineCacheData.evict("popularPosts");
		redisCacheData.evict("popularPosts");
		redisCacheData.evict("version");
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