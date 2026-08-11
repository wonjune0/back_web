CREATE TABLE orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number        VARCHAR(20) NOT NULL,
    user_id             BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PLACED',
    recipient_name      VARCHAR(100) NOT NULL,
    recipient_phone     VARCHAR(20) NOT NULL,
    zipcode             VARCHAR(10) NOT NULL,
    address1            VARCHAR(255) NOT NULL,
    address2            VARCHAR(255) NULL,
    delivery_request    VARCHAR(30) NOT NULL,
    payment_method      VARCHAR(20) NOT NULL,
    total_price         BIGINT NOT NULL,
    placed_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_user_id_placed_at ON orders(user_id, placed_at DESC);

CREATE TABLE order_items (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id                    BIGINT NOT NULL,
    product_id                  BIGINT NULL,
    product_name_snapshot       VARCHAR(255) NOT NULL,
    product_price_snapshot      BIGINT NOT NULL,
    quantity                    INT NOT NULL,
    subtotal                    BIGINT NOT NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    CONSTRAINT chk_order_items_quantity CHECK (quantity >= 1)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
