CREATE TABLE matching_session
(
    id  BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL
);

CREATE TABLE matching_member
(
    id  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    matching_session_id BIGINT NOT NULL,
    project_request_id BIGINT NOT NULL,
    is_accepted BOOLEAN NULL DEFAULT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at TIMESTAMP(6) NULL,
    deleted_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_matching_member_session FOREIGN KEY (matching_session_id) REFERENCES matching_session (id),
    CONSTRAINT fk_matching_member_project_request FOREIGN KEY (project_request_id) REFERENCES project_request (id)
);