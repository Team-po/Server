CREATE TABLE project_request (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    role              VARCHAR(255) NOT NULL,
    project_title       VARCHAR(255),
    project_description TEXT,
    project_mvp         TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'WAITING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    canceled_at DATETIME(6) NULL,
    CONSTRAINT fk_project_request_user FOREIGN KEY (user_id) REFERENCES users(id)
);