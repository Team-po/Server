UPDATE matching_member mm
JOIN matching_session ms ON ms.id = mm.matching_session_id
SET mm.deleted_at = ms.deleted_at
WHERE mm.deleted_at IS NULL
  AND ms.deleted_at IS NOT NULL;

ALTER TABLE matching_member
    ADD COLUMN active_project_request_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN project_request_id ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE INDEX uq_matching_member_active_project_request (active_project_request_id);
