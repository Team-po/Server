CREATE TABLE project_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    project_title VARCHAR(255) NOT NULL,
    project_description TEXT,
    project_mvp TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_project_group_match_id UNIQUE (match_id)
);

CREATE TABLE project_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_group_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    group_role VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_project_group_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_project_group_member_group FOREIGN KEY (project_group_id) REFERENCES project_group(id),
    CONSTRAINT uq_project_group_member_user UNIQUE (user_id),
    CONSTRAINT uq_project_group_member UNIQUE (project_group_id, user_id)
);
