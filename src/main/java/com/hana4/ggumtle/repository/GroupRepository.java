package com.hana4.ggumtle.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hana4.ggumtle.model.entity.group.Group;
import com.hana4.ggumtle.model.entity.group.GroupCategory;

public interface GroupRepository extends JpaRepository<Group, Long> {
	// 서브쿼리에서 먼저 사용자가 속한 그룹들을 필터링
	// 메인 쿼리에서는 필터링된 그룹들의 전체 멤버 수
	@Query(
		"SELECT g, COUNT(DISTINCT gm.id) " +
			"FROM Group g " +
			"LEFT JOIN GroupMember gm ON gm.group.id = g.id " +
			"WHERE (:userId IS NULL OR g.id IN (" +
			"    SELECT DISTINCT gm1.group.id " +
			"    FROM GroupMember gm1 " +
			"    WHERE gm1.user.id = :userId" +
			")) " +
			"AND (:category IS NULL OR g.category = :category) " +
			"AND (:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
			"GROUP BY g.id, g.name, g.category, g.description, g.imageUrl " +
			"ORDER BY " +
			"    CASE WHEN :userId IS NULL THEN COUNT(DISTINCT gm.id) END DESC, " +  // 모든 그룹 조회 시 멤버 수 많은 순 정렬
			"    CASE WHEN :userId IS NULL THEN MAX(gm.createdAt) END DESC, " +  // 모든 그룹 조회 시 최근 가입 순 추가 정렬
			"    CASE WHEN :userId IS NOT NULL THEN MAX(gm.createdAt) END DESC"  // 내 그룹 조회 시 최근 가입 순 정렬
	)
	Page<Object[]> findGroupsWithFilters(
		@Param("userId") String userId,
		@Param("category") GroupCategory category,
		@Param("search") String search,
		Pageable pageable
	);

	Group findFirstByOrderByIdAsc();
}
