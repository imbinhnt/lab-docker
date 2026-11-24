# Helm chart: java-web-demo

Chart này triển khai Spring Boot API lên Kubernetes với:

- `Deployment` (non-root, read-only root filesystem, rolling update)
- `Service` + `Ingress`
- `ConfigMap` cho cấu hình không bí mật
- `Secret` cho DB password / JWT / admin bootstrap (hoặc dùng `existingSecret`)
- `startupProbe` / `livenessProbe` / `readinessProbe` trỏ Actuator
- `resources.requests/limits`
- Flyway chạy bằng **init container** trước khi app start

## Yêu cầu

- `helm` >= 3.14
- `kubectl`
- `kind` hoặc `minikube`
- Docker

## Chạy thử với kind

```bash
# 1. Cluster
kind create cluster --name demo

# 2. Build image local và load vào kind
docker build -t your-dockerhub-username/java-web-demo:dev .
kind load docker-image your-dockerhub-username/java-web-demo:dev --name demo

# 3. PostgreSQL demo
kubectl create namespace demo
kubectl -n demo create deployment postgres --image=postgres:16-alpine
kubectl -n demo set env deployment/postgres \
  POSTGRES_DB=demo_db POSTGRES_USER=demo POSTGRES_PASSWORD=demo123
kubectl -n demo expose deployment postgres --port=5432 --name=postgres

# 4. Cài chart
helm upgrade --install demo deploy/helm/java-web-demo \
  --namespace demo \
  -f deploy/helm/java-web-demo/values-dev.yaml \
  --set image.repository=your-dockerhub-username/java-web-demo

# 5. Kiểm tra
kubectl -n demo rollout status deployment/demo-java-web-demo
kubectl -n demo get pods
kubectl -n demo port-forward svc/demo-java-web-demo 8080:80
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/v1/products
```

Ingress (tuỳ chọn):

```bash
# kind: cài ingress-nginx
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# minikube
minikube addons enable ingress

# thêm host
echo "127.0.0.1 java-web-demo.local" | sudo tee -a /etc/hosts
curl http://java-web-demo.local/api/v1/products
```

## Chạy thử với minikube

```bash
minikube start
eval "$(minikube docker-env)"          # build trực tiếp vào Docker của minikube
docker build -t your-dockerhub-username/java-web-demo:dev .

kubectl create namespace demo
kubectl -n demo create deployment postgres --image=postgres:16-alpine
kubectl -n demo set env deployment/postgres \
  POSTGRES_DB=demo_db POSTGRES_USER=demo POSTGRES_PASSWORD=demo123
kubectl -n demo expose deployment postgres --port=5432 --name=postgres

helm upgrade --install demo deploy/helm/java-web-demo \
  --namespace demo \
  -f deploy/helm/java-web-demo/values-dev.yaml \
  --set image.repository=your-dockerhub-username/java-web-demo

minikube service -n demo demo-java-web-demo
```

## Production

Tạo Secret trước, rồi trỏ chart vào đó:

```bash
kubectl create namespace demo
kubectl -n demo create secret generic java-web-demo-secrets \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=JWT_SECRET='...' \
  --from-literal=APP_SECURITY_USER='admin' \
  --from-literal=APP_SECURITY_PASSWORD='...'

helm upgrade --install demo deploy/helm/java-web-demo \
  --namespace demo \
  -f deploy/helm/java-web-demo/values-prod.yaml \
  --set image.repository=your-dockerhub-username/java-web-demo \
  --set image.tag=sha-abc1234
```

`values-prod.yaml` đặt `secret.existingSecret: java-web-demo-secrets`; chart sẽ
không tạo Secret từ values.

## Flyway

`flyway.initContainer.enabled=true` (mặc định) thêm init container chạy:

```text
java -jar /app/app.jar --spring.main.web-application-type=none --spring.flyway.enabled=true
```

Container này chạy migration + admin bootstrap rồi thoát; pod chính chỉ start khi
init container thành công. Flyway dùng lock trong PostgreSQL nên nhiều replica
khởi động đồng thời vẫn an toàn.

Tắt init container nếu muốn app tự migrate lúc start:

```bash
helm upgrade --install demo ... --set flyway.initContainer.enabled=false
```

## Kiểm tra chart

```bash
helm lint deploy/helm/java-web-demo -f deploy/helm/java-web-demo/values-dev.yaml
helm template demo deploy/helm/java-web-demo -f deploy/helm/java-web-demo/values-dev.yaml

# validate schema Kubernetes (cần Docker)
helm template demo deploy/helm/java-web-demo -f deploy/helm/java-web-demo/values-dev.yaml \
  | docker run --rm -i ghcr.io/yannh/kubeconform:latest -strict -summary -
```

## Values chính

| Key | Ý nghĩa |
|-----|---------|
| `image.repository`, `image.tag` | Image Docker Hub và tag (SHA do CI tạo) |
| `replicaCount` | Số pod |
| `config.springProfilesActive` | `dev`, `staging`, `prod` |
| `config.dbUrl`, `config.dbUser` | Kết nối PostgreSQL |
| `secret.existingSecret` | Dùng Secret có sẵn (khuyến nghị cho prod) |
| `secret.dbPassword`, `secret.jwtSecret`, `secret.adminPassword` | Chỉ dùng khi không có `existingSecret` |
| `ingress.*` | Bật/tắt và cấu hình Ingress |
| `probes.*` | Đường dẫn và timing của Actuator probes |
| `resources.*` | Requests/limits |
| `flyway.initContainer.enabled` | Bật/tắt init container migration |
