ALTER TABLE project_devguide
    DROP INDEX uk_project_devguide_project_group,
    DROP COLUMN updated_at,
    ADD COLUMN version_no INT NOT NULL DEFAULT 1 AFTER project_group_id,
    ADD COLUMN generation_type VARCHAR(20) NOT NULL DEFAULT 'INITIAL' AFTER version_no,
    ADD COLUMN is_confirmed BOOLEAN NOT NULL DEFAULT TRUE AFTER generation_type;

CREATE INDEX idx_project_devguide_group_confirmed ON project_devguide (project_group_id, is_confirmed);
CREATE INDEX idx_project_devguide_group_version ON project_devguide (project_group_id, version_no);