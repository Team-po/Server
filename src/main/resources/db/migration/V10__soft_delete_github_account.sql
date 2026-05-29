ALTER TABLE github_account
    DROP INDEX uq_github_account_github_user_id,
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD COLUMN active_github_user_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN github_user_id ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE INDEX uq_github_account_active_github_user_id (active_github_user_id);
