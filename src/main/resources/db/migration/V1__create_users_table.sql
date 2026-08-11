CREATE TABLE users (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email                       VARCHAR(255) NOT NULL,
    password_hash               VARCHAR(60)  NOT NULL,
    name                        VARCHAR(100) NOT NULL,
    phone                       VARCHAR(20)  NOT NULL,
    agree_age14                 BOOLEAN      NOT NULL,
    agree_terms_of_service      BOOLEAN      NOT NULL,
    agree_financial_terms       BOOLEAN      NOT NULL,
    agree_third_party_consent   BOOLEAN      NOT NULL,
    terms_agreed_at             TIMESTAMP    NOT NULL,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
