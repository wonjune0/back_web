package com.wonjune.backweb.category;

import com.wonjune.backweb.category.dto.CategoryTreeDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

	private final CategoryRepository categoryRepository;

	/**
	 * The tree is two levels deep and tiny (a handful of rows), so one findAll and an
	 * in-memory group beats a query per parent. Ordering by id keeps the sidebar in the
	 * order the seed migration declares rather than an arbitrary one.
	 */
	public List<CategoryTreeDto> getTree() {
		List<Category> all = categoryRepository.findAll();

		Map<Long, List<String>> childNamesByParentId = all.stream()
				.filter(category -> category.getParent() != null)
				.sorted(Comparator.comparing(Category::getId))
				.collect(Collectors.groupingBy(category -> category.getParent().getId(),
						Collectors.mapping(Category::getName, Collectors.toList())));

		return all.stream()
				.filter(category -> category.getParent() == null)
				.sorted(Comparator.comparing(Category::getId))
				.map(root -> new CategoryTreeDto(root.getName(),
						childNamesByParentId.getOrDefault(root.getId(), List.of())))
				.toList();
	}

}
