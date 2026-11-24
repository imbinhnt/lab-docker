# java-web-demo

Spring Boot 3.5 + PostgreSQL 16 + Flyway + JWT
## Kiến trúc

```text
Controller (API v1) → Service (@Transactional) → Repository (Spring Data JPA) → PostgreSQL
```

- DTO + MapStruct mapper, validation bằng Bean Validation.
- `filter/RequestLoggingFilter`: gắn `X-Request-Id` và `X-Correlation-Id`, log request/response.
- `security/`: Security + JWT, `UserDetailsService` lấy user từ DB, xử lý 401/403.
- `constant/ApiPaths`: đường dẫn API dùng chung.
- `config/`: CORS, OpenAPI, JPA Auditing.
- `exception/`: Global Exception Handler trả về `ApiError` thống nhất.
- Flyway quản lý schema (`src/main/resources/db/migration`); seed data tách riêng trong `db/seed`.
- Auditing: `created_at/updated_at`, `created_by/updated_by`, optimistic locking `@Version`.
- `deploy/ansible` và `deploy/helm`: triển khai lên VM hoặc Kubernetes.

## Yêu cầu

- JDK 17+ (CI và Docker dùng JDK 21)
- Docker + Docker Compose
- Không cần cài Maven — dùng Maven Wrapper (`./mvnw`)

## Chạy local

```bash
cp .env.example .env
docker compose up -d --build

curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/v1/products
```

Mặc định `SPRING_PROFILES_ACTIVE=dev`: API mở để tiện phát triển local.
Bật bảo vệ JWT bằng `SECURITY_PUBLIC_API=false`.

Chạy trực tiếp không qua Docker (cần PostgreSQL đang chạy):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Seed data

- `db/migration`: schema + role chuẩn (chạy ở **mọi** môi trường).
- `db/seed`: dữ liệu mẫu (category/product) chỉ chạy khi profile `dev`, `staging` hoặc `test`.
- `prod` chỉ chạy `db/migration`, không tự thêm dữ liệu mẫu.

## Profiles & secrets

| Profile   | Mục đích | Secret | Seed data |
|-----------|----------|--------|-----------|
| `dev`     | Local, có default an toàn | Không bắt buộc | Có |
| `staging` | Môi trường staging | Bắt buộc từ env | Có |
| `prod`    | Production, tắt Swagger | Bắt buộc từ env | Không |

`application.yml` không chứa default cho DB. Default tiện dụng chỉ nằm trong
`application-dev.yml`; staging/prod thiếu biến sẽ fail fast.

Các biến môi trường chính:

```text
SPRING_PROFILES_ACTIVE=dev|staging|prod
DB_URL, DB_USER, DB_PASSWORD
JWT_SECRET                  # >= 32 ký tự
JWT_EXPIRATION=PT1H
APP_SECURITY_USER           # admin bootstrap (tạo nếu chưa tồn tại)
APP_SECURITY_PASSWORD       # mật khẩu admin, được băm BCrypt khi lưu
CORS_ALLOWED_ORIGINS        # danh sách phân tách bằng dấu phẩy
```

> Admin không được seed bằng SQL. Khi khởi động, `AdminBootstrap` kiểm tra user
> `APP_SECURITY_USER`; nếu chưa có thì tạo mới với role `ROLE_ADMIN` và mật khẩu
> BCrypt. Đổi mật khẩu admin bằng cách cập nhật user trong DB hoặc xoá user rồi
> khởi động lại với biến môi trường mới.

## API

Phiên bản chính thức: `/api/v1`. Các alias `/api/...` được giữ để không phá vỡ client cũ.

- `GET/POST /api/v1/products`, `GET/PUT/DELETE /api/v1/products/{id}`
- `GET/POST /api/v1/categories`, `GET/PUT/DELETE /api/v1/categories/{id}`
- `POST /api/v1/auth/login` — nhận JWT
- `GET /actuator/health`, `GET /actuator/health/liveness`, `GET /actuator/health/readiness`
- Swagger UI: `/swagger-ui.html` (dev/staging), OpenAPI JSON: `/v3/api-docs`

### Phân quyền

| Thao tác | Yêu cầu |
|----------|---------|
| `GET /api/**` | Đã đăng nhập (mọi role) |
| `POST/PUT/PATCH/DELETE /api/**` | `ROLE_ADMIN` |
| `/actuator/health/**`, `/v3/api-docs`, `/swagger-ui/**`, `/api/v1/auth/**` | Public |
| Các endpoint khác | Đã đăng nhập |

Khi `app.security.public-api=true` (mặc định ở `dev`), toàn bộ API được mở để
tiện phát triển local. Ở staging/prod mặc định là `false`.

Role chuẩn được tạo bởi migration V4: `ROLE_ADMIN`, `ROLE_USER`.

### Đăng nhập và gọi API bảo vệ

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products
```

Với `dev`, đặt `SECURITY_PUBLIC_API=false` nếu muốn bắt buộc JWT.

Ví dụ 403 — user chỉ có `ROLE_USER` gọi thao tác ghi:

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"X-1","name":"X","price":1,"quantity":1,"categoryId":1}'
# HTTP/1.1 403  {"status":403,"error":"Forbidden","message":"Access denied",...}
```

### Correlation ID

Mọi request được gắn:

- `X-Request-Id`: sinh mới nếu client không gửi.
- `X-Correlation-Id`: dùng giá trị client/gateway gửi nếu hợp lệ, ngược lại bằng `X-Request-Id`.

Cả hai được đưa vào MDC (`requestId`, `correlationId`), xuất hiện trong log và
được trả lại trong response header để trace xuyên service.

## Test & coverage

```bash
./mvnw clean verify
```

- Unit test: Mockito (`JwtService`, `JwtAuthenticationFilter`, service).
- Integration test: `@SpringBootTest` + Testcontainers PostgreSQL 16 (cần Docker).
- Repository test: `@DataJpaTest` + Testcontainers.
- Security: 401 khi thiếu JWT, 403 khi user thường gọi API ghi.
- JaCoCo: báo cáo tại `target/site/jacoco/index.html`.
- Ngưỡng bắt buộc: **line ≥ 60%**, **branch ≥ 35%**.

## Docker

Dockerfile multi-stage:

1. Build bằng Maven Wrapper.
2. Runtime JRE Alpine, chạy user non-root `app`, có `HEALTHCHECK` gọi
   `/actuator/health/liveness`.

```bash
docker build -t <dockerhub-user>/java-web-demo:local .
docker compose up -d --build        # dev
docker compose logs -f app
docker inspect --format '{{.State.Health.Status}}' demo-app
```

## CI/CD

`.github/workflows/ci.yml`:

1. Build + test + JaCoCo trên mọi push/PR.
2. Build Docker image.
3. Push Docker Hub khi push lên `main` hoặc tag `v*`.

Cần cấu hình GitHub Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`.

### Jenkins

`Jenkinsfile` (declarative) chạy các stage:

1. `Checkout`
2. `Build` — `./mvnw -B -DskipTests clean package`
3. `Test` — `./mvnw -B test` + publish JUnit
4. `JaCoCo` — `./mvnw -B verify -DskipTests` + publish report, enforce ngưỡng
5. `Build image` — tag `sha-<short-sha>` và `<full-git-commit>`
6. `Push Docker Hub` — dùng credential `dockerhub`, push khi `main` hoặc tag
7. `Deploy (Ansible)` — chạy playbook trên inventory `staging`/`prod`

Credential cần tạo trong Jenkins:

- `dockerhub` (Username with password): Docker Hub user + access token.
- `ansible-vault-password` (Secret file): mật khẩu Ansible Vault.

## Triển khai

### Ansible (VM)

```bash
cd deploy/ansible
ansible-playbook -i inventory/staging/hosts.yml deploy.yml \
  --limit staging -e image_tag=sha-abc1234 -e app_env=staging --ask-vault-pass
```

- Role `docker`: cài Docker Engine + compose plugin.
- Role `app`: render `/etc/java-web-demo/app.env` (0600), pull image, chạy
  container `--restart unless-stopped`, chờ `HEALTHCHECK` healthy.
- Secret lấy từ Ansible Vault (`group_vars/vault.yml`) hoặc biến môi trường.

Chi tiết: [`deploy/ansible/README.md`](deploy/ansible/README.md).

### Kubernetes / Helm

```bash
helm upgrade --install demo deploy/helm/java-web-demo \
  --namespace demo --create-namespace \
  -f deploy/helm/java-web-demo/values-dev.yaml \
  --set image.repository=<dockerhub-user>/java-web-demo

kubectl -n demo port-forward svc/demo-java-web-demo 8080:80
```

- Deployment non-root, read-only root filesystem, rolling update.
- Probes: `/actuator/health/liveness` và `/actuator/health/readiness`.
- ConfigMap + Secret; prod dùng `secret.existingSecret`.
- Flyway chạy bằng init container trước khi app start.
- Có hướng dẫn kind/minikube: [`deploy/helm/java-web-demo/README.md`](deploy/helm/java-web-demo/README.md).

## Logging

`logback-spring.xml`:

- `dev`: log console dễ đọc, kèm `req`, `corr`, `user`.
- `staging`/`prod`: JSON structured log (logstash encoder), có `requestId`,
  `correlationId`, `userId` trong MDC — sẵn sàng cho ELK/Loki.
