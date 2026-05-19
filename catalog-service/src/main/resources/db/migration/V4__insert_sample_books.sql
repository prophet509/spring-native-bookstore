-- Insert sample books for development/testing
-- Using ISBNs that don't conflict with test data (test uses 1234567890)
INSERT INTO book (isbn, title, author, price, publisher) VALUES
    ('9781617296956', 'Cloud Native Spring in Action', 'Thomas Vitale', 49.90, 'Manning'),
    ('9781617298295', 'Spring Boot in Practice', 'Somnath Musib', 44.99, 'Manning'),
    ('9781617293986', 'Spring Microservices in Action', 'John Carnell', 49.99, 'Manning'),
    ('9781492091700', 'Reactive Spring', 'Josh Long', 39.99, 'O''Reilly'),
    ('9781617297731', 'Spring Security in Action', 'Laurentiu Spilca', 54.99, 'Manning'),
    ('9781484275989', 'Pro Spring 6', 'Iuliana Cosmina', 59.99, 'Apress'),
    ('9781492076974', 'Spring Boot Up & Running', 'Mark Heckler', 34.99, 'O''Reilly'),
    ('9781803233307', 'Learning Spring Boot 3.0', 'Greg L. Turnquist', 49.99, 'Packt'),
    ('9781484280013', 'Spring Framework 6', 'Felipe Gutierrez', 44.99, 'Apress'),
    ('9781617298028', 'Spring Data JPA', 'Christian Bauer', 39.99, 'Manning');