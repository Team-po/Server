ALTER TABLE github_account
    ADD INDEX idx_github_account_user_id (user_id),
    DROP INDEX uq_github_account_user_id,
    ADD COLUMN active_user_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN user_id ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE INDEX uq_github_account_active_user_id (active_user_id);
