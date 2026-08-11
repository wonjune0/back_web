package com.wonjune.backweb.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Query("""
			SELECT p FROM Product p
			JOIN FETCH p.category c
			WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
			  AND (:category IS NULL OR c.name = :category)
			  AND (:parentCategory IS NULL OR c.parent.name = :parentCategory)
			""")
	Page<Product> search(@Param("search") String search,
			@Param("category") String category,
			@Param("parentCategory") String parentCategory,
			Pageable pageable);

}
