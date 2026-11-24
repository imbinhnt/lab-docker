package com.example.demo.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.ProductRequest;
import com.example.demo.exception.ConflictException;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock ProductMapper mapper;
    @InjectMocks ProductServiceImpl service;

    @Test
    void create_throwsConflict_whenSkuAlreadyExists() {
        when(products.existsBySku("SKU-1")).thenReturn(true);

        ProductRequest req = new ProductRequest("SKU-1", "Name", BigDecimal.TEN, 1, 1L);

        assertThrows(ConflictException.class, () -> service.create(req));
        verify(products, never()).save(any());
    }
}
