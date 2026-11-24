INSERT INTO categories (name, description, created_at, updated_at) VALUES
    ('Laptop', 'Máy tính xách tay', now(), now()),
    ('Phụ kiện', 'Chuột, bàn phím, tai nghe', now(), now());

INSERT INTO products (sku, name, price, quantity, category_id, created_at, updated_at) VALUES
    ('LAP-001', 'Laptop Dev 16GB', 15990000.00, 10, (SELECT id FROM categories WHERE name = 'Laptop'), now(), now()),
    ('ACC-001', 'Chuột không dây',   250000.00, 100, (SELECT id FROM categories WHERE name = 'Phụ kiện'), now(), now()),
    ('ACC-002', 'Bàn phím cơ',       890000.00,  50, (SELECT id FROM categories WHERE name = 'Phụ kiện'), now(), now());
