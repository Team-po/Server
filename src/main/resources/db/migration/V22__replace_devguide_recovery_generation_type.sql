UPDATE project_devguide
SET generation_type = CASE
    WHEN version_no = 1 THEN 'INITIAL'
    ELSE 'MANUAL'
END
WHERE generation_type = 'RECOVERY';
