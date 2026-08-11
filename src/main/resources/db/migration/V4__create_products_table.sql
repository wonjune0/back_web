CREATE TABLE products (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id            BIGINT NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    image_url              VARCHAR(500) NOT NULL,
    original_price         BIGINT NULL,
    price                  BIGINT NOT NULL,
    delivery_badge         VARCHAR(20) NOT NULL,
    delivery_text          VARCHAR(255) NOT NULL,
    rating                 DECIMAL(2,1) NOT NULL DEFAULT 0.0,
    review_count           INT UNSIGNED NOT NULL DEFAULT 0,
    reward_amount          BIGINT NOT NULL DEFAULT 0,
    detail_description     TEXT NOT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_products_category_id ON products(category_id);
