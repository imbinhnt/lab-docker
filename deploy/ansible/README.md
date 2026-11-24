# Ansible deployment

Triển khai container `java-web-demo` lên một host đã có SSH (hoặc localhost cho dev).

## Cấu trúc

```text
deploy/ansible/
├── ansible.cfg
├── deploy.yml
├── inventory/
│   ├── dev/hosts.yml
│   ├── staging/hosts.yml
│   └── prod/hosts.yml
├── group_vars/
│   ├── all.yml
│   ├── dev.yml / staging.yml / prod.yml
│   └── vault.yml.example
└── roles/
    ├── docker/   # cài Docker Engine + compose plugin
    └── app/      # render env file, pull image, chạy container, chờ healthy
```

## Secret

Mỗi secret được resolve theo thứ tự:

1. Ansible Vault (`vault_*` trong `group_vars/vault.yml`).
2. Biến môi trường trên máy chạy playbook (`DB_PASSWORD`, `JWT_SECRET`, ...).
3. Không có → role `app` fail fast với thông báo rõ ràng.

Tạo vault:

```bash
cd deploy/ansible
cp group_vars/vault.yml.example group_vars/vault.yml
# sửa giá trị thật
ansible-vault encrypt group_vars/vault.yml
ansible-vault edit group_vars/vault.yml
```

`group_vars/vault.yml` đã nằm trong `.gitignore`.

## Cấu hình host

Inventory đọc host/user/key từ biến môi trường, ví dụ staging:

```bash
export STAGING_HOST=203.0.113.10
export STAGING_USER=ubuntu
export STAGING_SSH_KEY=~/.ssh/id_ed25519
```

Prod tương tự với `PROD_HOST`, `PROD_USER`, `PROD_SSH_KEY`.

## Chạy

```bash
cd deploy/ansible

# Kiểm tra cú pháp
ansible-playbook -i inventory/staging/hosts.yml deploy.yml --syntax-check

# Deploy image cụ thể (tag = git commit SHA do Jenkins truyền vào)
ansible-playbook -i inventory/staging/hosts.yml deploy.yml \
  --limit staging \
  -e image_tag=sha-abc1234 \
  -e app_env=staging \
  --ask-vault-pass

# Dev local (cần sudo để cài Docker; Docker đã có sẵn thì bỏ qua bước cài)
ansible-playbook -i inventory/dev/hosts.yml deploy.yml \
  --limit dev \
  -e image_tag=local \
  -e app_env=dev \
  --ask-vault-pass -K
```

Playbook:

1. Role `docker`: thêm repo chính thức, cài `docker-ce`, `docker-buildx-plugin`,
   `docker-compose-plugin`, bật service.
2. Role `app`: tạo `/etc/java-web-demo/app.env` (mode `0600`), `docker login`
   nếu có token, `docker pull`, thay container cũ, chạy container mới với
   `--restart unless-stopped`, rồi chờ `HEALTHCHECK` chuyển `healthy`.

## Lưu ý

- Ansible không build image; Jenkins/CI build và push Docker Hub trước.
- Cùng một image được dùng cho staging/prod; khác nhau ở env file và profile.
- Không chạy `prod` bằng `--check` trên host thật khi chưa hiểu rõ thay đổi.
