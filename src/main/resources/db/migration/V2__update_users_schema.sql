ALTER TABLE users
DROP
COLUMN user_id,
    ADD CONSTRAINT uq_users_email UNIQUE (email),
    RENAME COLUMN profileImage TO profile_image;
