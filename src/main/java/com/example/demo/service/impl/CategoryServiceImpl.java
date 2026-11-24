package com.example.demo.service.impl;

import com.example.demo.dto.CategoryRequest;
import com.example.demo.dto.CategoryResponse;
import com.example.demo.entity.Category;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.mapper.CategoryMapper;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.CategoryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CategoryMapper mapper;

    public CategoryServiceImpl(CategoryRepository categories, ProductRepository products, CategoryMapper mapper) {
        this.categories = categories;
        this.products = products;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categories.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return mapper.toResponse(findCategory(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categories.existsByName(request.name())) {
            throw new ConflictException("Category '" + request.name() + "' already exists");
        }
        Category category = mapper.toEntity(request);
        categories.save(category);
        return mapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        if (categories.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Category '" + request.name() + "' already exists");
        }
        mapper.updateEntity(request, category);
        return mapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);
        if (products.existsByCategoryId(id)) {
            throw new ConflictException("Category " + id + " still has products");
        }
        categories.delete(category);
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
