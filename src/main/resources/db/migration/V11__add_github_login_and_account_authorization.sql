ALTER TABLE users
    ADD COLUMN is_github_login BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE github_account
    ADD COLUMN access_token_ciphertext TEXT NULL,
    ADD COLUMN token_type VARCHAR(50) NULL,
    ADD COLUMN github_scopes VARCHAR(1000) NULL,
    ADD COLUMN connected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN token_updated_at TIMESTAMP(6) NULL;
