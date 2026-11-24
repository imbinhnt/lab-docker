package com.example.demo.constant;

/**
 * Đường dẫn API dùng chung. `/api/v1` là phiên bản chính thức; các alias
 * `/api/...` được giữ lại để không phá vỡ client hiện có.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";

    public static final String PRODUCTS = V1 + "/products";
    public static final String CATEGORIES = V1 + "/categories";
    public static final String AUTH = V1 + "/auth";

    public static final String LEGACY_PRODUCTS = "/api/products";
    public static final String LEGACY_CATEGORIES = "/api/categories";

    private ApiPaths() {
    }
}
