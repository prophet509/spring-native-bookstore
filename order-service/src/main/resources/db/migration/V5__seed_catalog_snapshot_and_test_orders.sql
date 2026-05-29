-- Seed catalog_book_snapshot for local development/testing (no Kafka needed)
INSERT INTO catalog_book_snapshot (isbn, title, price, updated_at) VALUES
    ('9781617296956', 'Cloud Native Spring in Action', 49.90, NOW()),
    ('9781617298295', 'Spring Boot in Practice', 44.99, NOW()),
    ('9781617293986', 'Spring Microservices in Action', 49.99, NOW()),
    ('9781492091700', 'Reactive Spring', 39.99, NOW()),
    ('9781617297731', 'Spring Security in Action', 54.99, NOW()),
    ('9781484275989', 'Pro Spring 6', 59.99, NOW()),
    ('9781492076974', 'Spring Boot Up & Running', 34.99, NOW()),
    ('9781803233307', 'Learning Spring Boot 3.0', 49.99, NOW()),
    ('9781484280013', 'Spring Framework 6', 44.99, NOW()),
    ('9781617298028', 'Spring Data JPA', 39.99, NOW()),
    ('9781492057512', 'Kubernetes Best Practices', 54.99, NOW()),
    ('9781098133521', 'Microservices Security in Action', 49.99, NOW()),
    ('9781492084907', 'Observability Engineering', 59.99, NOW()),
    ('9781835085240', 'Spring Boot 4 Cookbook', 44.99, NOW()),
    ('9781617293153', 'Spring in Action', 52.99, NOW())
ON CONFLICT (isbn) DO NOTHING;

-- Fix existing sample orders to include the actual title from the snapshot
UPDATE orders SET book_name = 'Cloud Native Spring in Action', book_price = 49.90 WHERE book_isbn = '9781617296956';
UPDATE orders SET book_name = 'Spring Boot in Practice', book_price = 44.99 WHERE book_isbn = '9781617298295';
UPDATE orders SET book_name = 'Spring Microservices in Action', book_price = 49.99 WHERE book_isbn = '9781617293986';
UPDATE orders SET book_name = 'Reactive Spring', book_price = 39.99 WHERE book_isbn = '9781492091700';
UPDATE orders SET book_name = 'Spring Security in Action', book_price = 54.99 WHERE book_isbn = '9781617297731';
UPDATE orders SET book_name = 'Pro Spring 6', book_price = 59.99 WHERE book_isbn = '9781484275989';
UPDATE orders SET book_name = 'Spring Boot Up & Running', book_price = 34.99 WHERE book_isbn = '9781492076974';
UPDATE orders SET book_name = 'Learning Spring Boot 3.0', book_price = 49.99 WHERE book_isbn = '9781803233307';
UPDATE orders SET book_name = 'Spring Framework 6', book_price = 44.99 WHERE book_isbn = '9781484280013';
UPDATE orders SET book_name = 'Spring Data JPA', book_price = 39.99 WHERE book_isbn = '9781617298028';

-- Add diverse sample orders across all statuses and users
INSERT INTO orders (book_isbn, book_name, book_price, quantity, status, created_date, last_modified_date, version, created_by, last_modified_by) VALUES
    -- ACCEPTED orders (simulating fulfilled inventory reserve)
    ('9781617296956', 'Cloud Native Spring in Action', 49.90, 1, 'ACCEPTED',  NOW() - INTERVAL '12 days', NOW() - INTERVAL '11 days', 1, 'alice', 'alice'),
    ('9781617298295', 'Spring Boot in Practice',        44.99, 2, 'ACCEPTED',  NOW() - INTERVAL '10 days', NOW() - INTERVAL '9 days',  1, 'alice', 'alice'),
    ('9781492091700', 'Reactive Spring',                39.99, 1, 'ACCEPTED',  NOW() - INTERVAL '8 days',  NOW() - INTERVAL '7 days',  1, 'bob',   'bob'),
    ('9781617297731', 'Spring Security in Action',       54.99, 3, 'ACCEPTED',  NOW() - INTERVAL '6 days',  NOW() - INTERVAL '5 days',  1, 'charlie', 'charlie'),

    -- DISPATCHED orders
    ('9781617293986', 'Spring Microservices in Action',  49.99, 2, 'DISPATCHED', NOW() - INTERVAL '14 days', NOW() - INTERVAL '12 days', 2, 'diana', 'diana'),
    ('9781492057512', 'Kubernetes Best Practices',       54.99, 1, 'DISPATCHED', NOW() - INTERVAL '13 days', NOW() - INTERVAL '11 days', 2, 'eric',  'eric'),
    ('9781484275989', 'Pro Spring 6',                    59.99, 1, 'DISPATCHED', NOW() - INTERVAL '11 days', NOW() - INTERVAL '9 days',  2, 'fiona', 'fiona'),

    -- REJECTED orders (insufficient stock or missing book)
    ('9781098133521', 'Microservices Security in Action', 49.99, 5, 'REJECTED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', 0, 'grace', 'grace'),
    ('9781492084907', 'Observability Engineering',        59.99, 3, 'REJECTED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', 0, 'grace', 'grace'),
    ('9781835085240', 'Spring Boot 4 Cookbook',           44.99, 10, 'REJECTED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', 0, 'henry', 'henry'),

    -- PENDING orders (awaiting inventory reserve)
    ('9781617293153', 'Spring in Action',                52.99, 2, 'PENDING',  NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours',  0, 'ivy',  'ivy'),
    ('9781492076974', 'Spring Boot Up & Running',        34.99, 1, 'PENDING',  NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '45 minutes', 0, 'jack', 'jack'),
    ('9781484280013', 'Spring Framework 6',              44.99, 3, 'PENDING',  NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes', 0, 'kate', 'kate'),

    -- Flash-sale style: many copies of same book by multiple users
    ('9781492057512', 'Kubernetes Best Practices',  54.99, 1, 'PENDING', NOW(), NOW(), 0, 'liam', 'liam'),
    ('9781492057512', 'Kubernetes Best Practices',  54.99, 2, 'PENDING', NOW(), NOW(), 0, 'maya', 'maya'),
    ('9781492057512', 'Kubernetes Best Practices',  54.99, 1, 'PENDING', NOW(), NOW(), 0, 'noah', 'noah'),
    ('9781803233307', 'Learning Spring Boot 3.0',   49.99, 1, 'PENDING', NOW(), NOW(), 0, 'olivia', 'olivia');
