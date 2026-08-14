INSERT INTO book (isbn, title, author, price, publisher)
SELECT '9781617296956', 'Cloud Native Spring in Action', 'Thomas Vitale', 49.90, 'Manning'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book WHERE isbn = '9781617296956');

INSERT INTO book (isbn, title, author, price, publisher)
SELECT '9781617298295', 'Spring Boot in Practice', 'Somnath Musib', 44.99, 'Manning'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book WHERE isbn = '9781617298295');
