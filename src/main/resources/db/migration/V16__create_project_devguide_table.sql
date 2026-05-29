CREATE TABLE project_devguide (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id BIGINT NOT NULL,
    overview TEXT NOT NULL,
    tech_stack JSON NOT NULL,
    mvp_priorities JSON NOT NULL,
    decision_points JSON NOT NULL,
    milestones JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,

    CONSTRAINT uk_project_devguide_project_group UNIQUE (project_group_id),
    CONSTRAINT fk_project_devguide_group FOREIGN KEY (project_group_id) REFERENCES project_group(id)
);

CREATE INDEX idx_project_devguide_project_group_id ON project_devguide (project_group_id);
CREATE INDEX idx_project_devguide_deleted_at ON project_devguide (deleted_at);