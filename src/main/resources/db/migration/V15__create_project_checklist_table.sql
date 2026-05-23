CREATE TABLE project_checklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL,
    due_date DATE NULL,
    ai_advice TEXT NULL,
    assignee_user_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_project_checklist_group FOREIGN KEY (project_group_id) REFERENCES project_group(id),
    CONSTRAINT fk_project_checklist_assignee FOREIGN KEY (assignee_user_id) REFERENCES users(id),
    CONSTRAINT fk_project_checklist_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_project_checklist_project_group_id ON project_checklist (project_group_id);
CREATE INDEX idx_project_checklist_project_group_status ON project_checklist (project_group_id, status);
