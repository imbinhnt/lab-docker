package com.example.demo.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.CategoryRequest;
import com.example.demo.dto.CategoryResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.entity.Product;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ProductRepository products;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void healthAndOpenApiArePublic() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");

        ResponseEntity<String> openApi = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody()).contains("/api/v1/products");
    }

    @Test
    void productsRequireJwt() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/products", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getBody()).contains("Authentication required");
    }

    @Test
    void loginRejectsInvalidCredentials() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest("test-admin", "wrong-password"),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createProductUsesJwtVersioningAndAuditing() {
        String token = login().accessToken();
        HttpHeaders headers = authorizedHeaders(token);
        String sku = "IT-" + UUID.randomUUID();

        ProductRequest createRequest =
                new ProductRequest(sku, "Integration product", new BigDecimal("12.34"), 5, 1L);
        ResponseEntity<ProductResponse> created = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getHeaders().getLocation())
                .hasPath("/api/v1/products/" + created.getBody().id());

        Product persisted = products.findById(created.getBody().id()).orElseThrow();
        assertThat(persisted.getCreatedBy()).isEqualTo("test-admin");
        assertThat(persisted.getUpdatedBy()).isEqualTo("test-admin");
        assertThat(persisted.getVersion()).isZero();

        ProductRequest updateRequest =
                new ProductRequest(sku, "Updated product", new BigDecimal("99.99"), 7, 1L);
        ResponseEntity<ProductResponse> updated = rest.exchange(
                "/api/v1/products/" + persisted.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                ProductResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        Product reloaded = products.findById(persisted.getId()).orElseThrow();
        assertThat(reloaded.getUpdatedBy()).isEqualTo("test-admin");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void categoryLifecycleAndNotFound() {
        HttpHeaders headers = authorizedHeaders(login().accessToken());
        String name = "IT-CAT-" + UUID.randomUUID();

        ResponseEntity<CategoryResponse> created = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest(name, "integration category"), headers),
                CategoryResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getHeaders().getLocation())
                .hasPath("/api/v1/categories/" + created.getBody().id());

        ResponseEntity<CategoryResponse> fetched = rest.exchange(
                "/api/v1/categories/" + created.getBody().id(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CategoryResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo(name);

        ResponseEntity<CategoryResponse> updated = rest.exchange(
                "/api/v1/categories/" + created.getBody().id(),
                HttpMethod.PUT,
                new HttpEntity<>(new CategoryRequest(name + "-v2", "updated"), headers),
                CategoryResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo(name + "-v2");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/categories/" + created.getBody().id(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> missing = rest.exchange(
                "/api/v1/categories/" + created.getBody().id(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("not found");
    }

    @Test
    void duplicateCategoryNameReturnsConflict() {
        HttpHeaders headers = authorizedHeaders(login().accessToken());
        String name = "IT-DUP-" + UUID.randomUUID();

        ResponseEntity<CategoryResponse> first = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest(name, "first"), headers),
                CategoryResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(new CategoryRequest(name, "second"), headers),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("already exists");
    }

    @Test
    void writeEndpointsRejectNonAdminUser() {
        ensureUser("test-user", "test-user-password", "ROLE_USER");
        HttpHeaders headers = authorizedHeaders(login("test-user", "test-user-password").accessToken());

        ResponseEntity<String> read = rest.exchange(
                "/api/v1/products", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProductRequest request = new ProductRequest(
                "IT-USER-" + UUID.randomUUID(), "Forbidden product", BigDecimal.ONE, 1, 1L);
        ResponseEntity<String> write = rest.exchange(
                "/api/v1/products", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getBody()).contains("Access denied");
    }

    @Test
    void correlationIdHeaderIsEchoed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "corr-integration-1");

        ResponseEntity<String> response = rest.exchange(
                "/actuator/health/liveness", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-integration-1");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");
        headers.setAccessControlRequestMethod(HttpMethod.GET);

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/products", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
    }

    @Test
    void legacyApiPathStillWorks() {
        HttpHeaders headers = authorizedHeaders(login().accessToken());

        ResponseEntity<String> response = rest.exchange(
                "/api/products", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"items\"");
    }

    private LoginResponse login() {
        return login("test-admin", "test-password");
    }

    private LoginResponse login(String username, String password) {
        ResponseEntity<LoginResponse> response = rest.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(username, password),
                LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void ensureUser(String username, String rawPassword, String roleName) {
        if (users.existsByUsername(username)) {
            return;
        }
        Role role = roles.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.getRoles().add(role);
        users.save(user);
    }

    private HttpHeaders authorizedHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
