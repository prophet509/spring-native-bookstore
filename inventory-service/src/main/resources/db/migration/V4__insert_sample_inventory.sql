-- Insert sample inventory items matching the books in catalog-service
INSERT INTO inventory (isbn, available_quantity, reserved_quantity) VALUES
    ('9781617296956', 100, 0),
    ('9781617298295', 50, 0),
    ('9781617293986', 75, 0),
    ('9781492091700', 60, 0),
    ('9781617297731', 40, 0),
    ('9781484275989', 30, 0),
    ('9781492076974', 80, 0),
    ('9781803233307', 25, 0),
    ('9781484280013', 35, 0),
    ('9781617298028', 45, 0);