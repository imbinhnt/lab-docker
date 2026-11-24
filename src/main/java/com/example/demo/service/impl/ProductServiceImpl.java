package com.example.demo.service.impl;

import com.example.demo.dto.PageResponse;
import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductMapper mapper;

    public ProductServiceImpl(ProductRepository products, CategoryRepository categories, ProductMapper mapper) {
        this.products = products;
        this.categories = categories;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(Long categoryId, Pageable pageable) {
        Page<Product> page = (categoryId == null)
                ? products.findAll(pageable)
                : products.findByCategoryId(categoryId, pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return mapper.toResponse(findProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        Category category = findCategory(request.categoryId());

        Product product = mapper.toEntity(request);
        product.setCategory(category);
        products.save(product);
        return mapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        if (products.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        Category category = findCategory(request.categoryId());

        mapper.updateEntity(request, product);
        product.setCategory(category);
        return mapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findProduct(id);
        products.delete(product);
    }

    private Product findProduct(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
