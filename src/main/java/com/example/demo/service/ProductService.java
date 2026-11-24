package com.example.demo.service;

import com.example.demo.dto.PageResponse;
import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    PageResponse<ProductResponse> findAll(Long categoryId, Pageable pageable);

    ProductResponse getById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}
