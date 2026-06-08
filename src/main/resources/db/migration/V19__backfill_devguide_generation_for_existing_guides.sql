INSERT IGNORE INTO project_devguide_generation (project_group_id, status, max_regeneration_count, created_at, updated_at)
SELECT DISTINCT project_group_id, 'COMPLETED', 3, NOW(6), NOW(6)
FROM project_devguide
WHERE deleted_at IS NULL
  AND is_confirmed = TRUE;