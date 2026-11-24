package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        BigDecimal price,
        Integer quantity,
        Long categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt) {
}
