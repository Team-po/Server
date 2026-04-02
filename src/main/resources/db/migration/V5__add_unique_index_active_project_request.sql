CREATE UNIQUE INDEX uq_active_request_per_user
ON project_request(user_id)
WHERE status IN ('WAITING', 'MATCHING', 'MATCHED');