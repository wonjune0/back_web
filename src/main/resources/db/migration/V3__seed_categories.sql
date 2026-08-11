INSERT INTO categories (name, parent_id) VALUES ('뷰티', NULL);
SET @beauty = LAST_INSERT_ID();
INSERT INTO categories (name, parent_id) VALUES
    ('스킨케어', @beauty),
    ('클렌징', @beauty),
    ('선케어', @beauty),
    ('메이크업', @beauty);

INSERT INTO categories (name, parent_id) VALUES ('식품', NULL);
SET @food = LAST_INSERT_ID();
INSERT INTO categories (name, parent_id) VALUES
    ('신선식품', @food),
    ('원두/음료', @food),
    ('간식/과자', @food);

INSERT INTO categories (name, parent_id) VALUES ('생활용품', NULL);
SET @living = LAST_INSERT_ID();
INSERT INTO categories (name, parent_id) VALUES
    ('침구', @living),
    ('청소/세탁', @living),
    ('방향/탈취', @living);

INSERT INTO categories (name, parent_id) VALUES ('가전디지털', NULL);
SET @digital = LAST_INSERT_ID();
INSERT INTO categories (name, parent_id) VALUES
    ('소형가전', @digital),
    ('PC/주변기기', @digital);

INSERT INTO categories (name, parent_id) VALUES ('스포츠/레저', NULL);
SET @sports = LAST_INSERT_ID();
INSERT INTO categories (name, parent_id) VALUES
    ('운동복/신발', @sports),
    ('캠핑용품', @sports);
