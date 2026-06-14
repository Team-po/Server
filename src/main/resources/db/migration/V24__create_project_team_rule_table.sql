CREATE TABLE project_team_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id BIGINT NOT NULL,
    content_md TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    updated_by_user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT uk_project_team_rule_project_group UNIQUE (project_group_id),
    CONSTRAINT fk_project_team_rule_group FOREIGN KEY (project_group_id) REFERENCES project_group(id),
    CONSTRAINT fk_project_team_rule_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_project_team_rule_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users(id)
);
