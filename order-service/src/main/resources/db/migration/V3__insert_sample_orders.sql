-- Insert sample orders for development/testing
INSERT INTO orders (book_isbn, book_name, book_price, quantity, status, created_date, last_modified_date, version, created_by, last_modified_by) VALUES
    ('9781617296956', 'Cloud Native Spring in Action', 49.90, 2, 'PENDING', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', 0, 'bjorn', 'bjorn'),
    ('9781617298295', 'Spring Boot in Practice', 44.99, 1, 'PENDING', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days', 0, 'bjorn', 'bjorn'),
    ('9781617293986', 'Spring Microservices in Action', 49.99, 3, 'PENDING', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', 0, 'bjorn', 'bjorn'),
    ('9781492091700', 'Reactive Spring', 39.99, 1, 'PENDING', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 0, 'bjorn', 'bjorn'),
    ('9781617297731', 'Spring Security in Action', 54.99, 2, 'PENDING', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 0, 'bjorn', 'bjorn'),
    ('9781484275989', 'Pro Spring 6', 59.99, 1, 'PENDING', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours', 0, 'bjorn', 'bjorn'),
    ('9781492076974', 'Spring Boot Up & Running', 34.99, 4, 'PENDING', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours', 0, 'bjorn', 'bjorn'),
    ('9781803233307', 'Learning Spring Boot 3.0', 49.99, 1, 'PENDING', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour', 0, 'bjorn', 'bjorn'),
    ('9781484280013', 'Spring Framework 6', 44.99, 2, 'PENDING', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes', 0, 'bjorn', 'bjorn'),
    ('9781617298028', 'Spring Data JPA', 39.99, 1, 'PENDING', NOW(), NOW(), 0, 'bjorn', 'bjorn');