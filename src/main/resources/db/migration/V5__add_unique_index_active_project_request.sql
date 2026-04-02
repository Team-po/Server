ALTER TABLE project_request
    ADD COLUMN active_user_id BIGINT AS (
    CASE WHEN status IN ('WAITING', 'MATCHING', 'MATCHED') THEN user_id ELSE NULL END
) VIRTUAL,
ADD UNIQUE INDEX uq_active_request_per_user (active_user_id);