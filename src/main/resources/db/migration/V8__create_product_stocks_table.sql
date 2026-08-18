-- Stock lives apart from products on purpose: the catalog is read-heavy and cacheable,
-- while stock is write-heavy and is the row every concurrent checkout contends on.
-- Keeping them in one table would mean every stock lock also blocks catalog writes.
CREATE TABLE product_stocks (
    product_id  BIGINT PRIMARY KEY,
    quantity    INT NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_stocks_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_product_stocks_quantity CHECK (quantity >= 0)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Quantities are spread over 30~100 but written out literally rather than generated:
-- a migration has to produce the same database everywhere, and the concurrency tests
-- assert on exact remaining counts.
INSERT INTO product_stocks (product_id, quantity) VALUES
(1, 42), (2, 88), (3, 57), (4, 31), (5, 64), (6, 96), (7, 38), (8, 73),
(9, 50), (10, 45), (11, 82), (12, 61), (13, 34), (14, 91), (15, 47), (16, 30);
