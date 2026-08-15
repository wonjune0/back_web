-- uk_payments_idempotency_key is the last line of defence against a double charge.
-- An application-level "have I seen this key?" check can be raced past; a unique index
-- cannot, so the insert is what actually decides which concurrent request wins.
CREATE TABLE payments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id           BIGINT NOT NULL,
    idempotency_key    VARCHAR(64) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    amount             BIGINT NOT NULL,
    pg_transaction_id  VARCHAR(100) NULL,
    failure_reason     VARCHAR(255) NULL,
    requested_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMP NULL,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Orders used to be born completed. They are now born PENDING and only reach PAID once
-- the gateway approves, so the rows written before this migration are the completed ones.
UPDATE orders SET status = 'PAID' WHERE status = 'PLACED';
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';

-- Supports sweeping orders that were left PENDING by a crash between the two transactions.
CREATE INDEX idx_orders_status_placed_at ON orders(status, placed_at);
