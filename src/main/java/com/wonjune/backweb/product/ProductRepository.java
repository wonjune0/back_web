package com.wonjune.backweb.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	/**
	 * Fetches category and its parent up front -- both are LAZY @ManyToOne, and the DTO
	 * mapping runs after the transaction closes (open-in-view is off), so leaving either
	 * as a proxy throws LazyInitializationException. The parent join is a LEFT JOIN with
	 * an alias so top-level categories (parent_id IS NULL) are not silently filtered out.
	 */
	@Query(value = """
			SELECT p FROM Product p
			JOIN FETCH p.category c
			LEFT JOIN FETCH c.parent pc
			WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:category IS NULL OR c.name = :category)
			  AND (:parentCategory IS NULL OR pc.name = :parentCategory)
			""",
			countQuery = """
			SELECT COUNT(p) FROM Product p
			JOIN p.category c
			LEFT JOIN c.parent pc
			WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:category IS NULL OR c.name = :category)
			  AND (:parentCategory IS NULL OR pc.name = :parentCategory)
			""")
	Page<Product> search(@Param("search") String search,
			@Param("category") String category,
			@Param("parentCategory") String parentCategory,
			Pageable pageable);

	@Query("""
			SELECT p FROM Product p
			JOIN FETCH p.category c
			LEFT JOIN FETCH c.parent pc
			WHERE p.id = :id
			""")
	Optional<Product> findDetailById(@Param("id") Long id);

}
