package com.hana4.ggumtle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.hana4.ggumtle.model.entity.postLike.PostLike;

import io.lettuce.core.dynamic.annotation.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
	int countByPostId(Long postId);

	List<PostLike> findByPostId(Long postId);

	Optional<PostLike> findByPostIdAndUserId(Long postId, String userId);

	@Query("SELECT pl.user.id FROM PostLike pl WHERE pl.post.id = :postId")
	List<String> findUserIdByPostId(@Param("postId") Long postId);

	void deleteByPostIdAndUserId(Long postId, String userId);
}
