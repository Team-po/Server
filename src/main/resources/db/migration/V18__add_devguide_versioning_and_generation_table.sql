ALTER TABLE project_devguide
    DROP INDEX uk_project_devguide_project_group,
    DROP COLUMN updated_at,
    ADD COLUMN version_no INT NOT NULL DEFAULT 1 AFTER project_group_id,
    ADD COLUMN generation_type VARCHAR(20) NOT NULL DEFAULT 'INITIAL' AFTER version_no,
    ADD COLUMN is_confirmed BOOLEAN NOT NULL DEFAULT TRUE AFTER generation_type;

CREATE INDEX idx_project_devguide_group_confirmed ON project_devguide (project_group_id, is_confirmed);
CREATE INDEX idx_project_devguide_group_version ON project_devguide (project_group_id, version_no);

CREATE TABLE project_devguide_generation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_regeneration_count INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,

    CONSTRAINT uk_devguide_generation_project_group UNIQUE (project_group_id),
    CONSTRAINT fk_devguide_generation_group FOREIGN KEY (project_group_id) REFERENCES project_group(id)
);
