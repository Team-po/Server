CREATE TABLE github_account
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    github_user_id  BIGINT       NOT NULL,
    github_username VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_github_account_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT uq_github_account_user_id
        UNIQUE (user_id),

    CONSTRAINT uq_github_account_github_user_id
        UNIQUE (github_user_id)
);
