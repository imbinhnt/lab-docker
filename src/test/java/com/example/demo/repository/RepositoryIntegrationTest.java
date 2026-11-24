package com.example.demo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.AuditConfig;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RepositoryIntegrationTest {

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
    CategoryRepository categories;

    @Autowired
    ProductRepository products;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    EntityManager entityManager;

    @Test
    void categoryRepositorySupportsCrudAndNameChecks() {
        Category category = new Category();
        category.setName("Repository-Test");
        category.setDescription("created by RepositoryIntegrationTest");

        Category saved = categories.saveAndFlush(category);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(categories.existsByName("Repository-Test")).isTrue();
        assertThat(categories.existsByNameAndIdNot("Repository-Test", saved.getId())).isFalse();
        assertThat(categories.findById(saved.getId())).isPresent();

        categories.delete(saved);
        categories.flush();
        assertThat(categories.existsByName("Repository-Test")).isFalse();
    }

    @Test
    void productRepositoryFindsSeededDataAndFiltersByCategory() {
        assertThat(products.existsBySku("LAP-001")).isTrue();
        assertThat(products.existsBySku("DOES-NOT-EXIST")).isFalse();

        Page<Product> all = products.findAll(PageRequest.of(0, 10));
        assertThat(all.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(all.getContent()).allSatisfy(product -> {
            assertThat(product.getCategory()).isNotNull();
            assertThat(product.getCategory().getName()).isNotBlank();
        });

        Long categoryId = all.getContent().get(0).getCategory().getId();
        Page<Product> byCategory = products.findByCategoryId(categoryId, PageRequest.of(0, 10));
        assertThat(byCategory.getContent()).isNotEmpty();
        assertThat(byCategory.getContent())
                .allSatisfy(product -> assertThat(product.getCategory().getId()).isEqualTo(categoryId));
    }

    @Test
    void userRepositoryLoadsRolesWithEntityGraph() {
        Role userRole = roles.findByName("ROLE_USER").orElseThrow();

        User user = new User();
        user.setUsername("repo-user");
        user.setPassword("$2a$10$not-a-real-hash-but-long-enough-for-column");
        user.getRoles().add(userRole);
        users.saveAndFlush(user);

        entityManager.clear();

        User loaded = users.findByUsername("repo-user").orElseThrow();
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
        assertThat(users.existsByUsername("repo-user")).isTrue();
    }

    @Test
    void seededRolesArePresent() {
        assertThat(roles.findByName("ROLE_ADMIN")).isPresent();
        assertThat(roles.findByName("ROLE_USER")).isPresent();
    }

    @Test
    void productPriceIsStoredWithTwoDecimalScale() {
        Product product = products.findAll(PageRequest.of(0, 1)).getContent().get(0);
        assertThat(product.getPrice())
                .isEqualByComparingTo(product.getPrice().setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(product.getPrice()).isGreaterThan(BigDecimal.ZERO);
    }
}
