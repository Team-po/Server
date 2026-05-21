CREATE TABLE IF NOT EXISTS github_installation
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    installation_id        BIGINT       NOT NULL,
    account_id             BIGINT       NOT NULL,
    account_login          VARCHAR(255) NOT NULL,
    account_type           VARCHAR(30)  NOT NULL,
    created_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at             TIMESTAMP(6) NULL,
    active_installation_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN installation_id ELSE NULL END
    ) VIRTUAL,
    active_account_id      BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN account_id ELSE NULL END
    ) VIRTUAL,

    CONSTRAINT uq_github_installation_active_installation_id
        UNIQUE (active_installation_id),

    CONSTRAINT uq_github_installation_active_account_id
        UNIQUE (active_account_id),

    CONSTRAINT ck_github_installation_account_type
        CHECK (account_type = 'Organization')
);

CREATE TABLE IF NOT EXISTS project_group_github_installation
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id        BIGINT       NOT NULL,
    github_installation_id   BIGINT       NOT NULL,
    connected_by            BIGINT       NOT NULL,
    created_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at              TIMESTAMP(6) NULL,
    active_project_group_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN project_group_id ELSE NULL END
    ) VIRTUAL,

    CONSTRAINT fk_project_group_github_installation_project_group
        FOREIGN KEY (project_group_id) REFERENCES project_group (id),

    CONSTRAINT fk_project_group_github_installation_github_installation
        FOREIGN KEY (github_installation_id) REFERENCES github_installation (id),

    CONSTRAINT fk_project_group_github_installation_connected_by
        FOREIGN KEY (connected_by) REFERENCES users (id),

    CONSTRAINT uq_project_group_github_installation_active_project_group_id
        UNIQUE (active_project_group_id)
);

CREATE TABLE IF NOT EXISTS project_group_github_repository
(
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id            BIGINT       NOT NULL,
    github_installation_id      BIGINT       NOT NULL,
    github_repository_id        BIGINT       NOT NULL,
    owner                       VARCHAR(255) NOT NULL,
    repo_name                   VARCHAR(255) NOT NULL,
    full_name                   VARCHAR(511) NOT NULL,
    default_branch              VARCHAR(255) NULL,
    is_private                  BOOLEAN      NOT NULL,
    created_at                  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at                  TIMESTAMP(6) NULL,
    active_project_group_id     BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN project_group_id ELSE NULL END
    ) VIRTUAL,
    active_github_repository_id BIGINT AS (
        CASE WHEN deleted_at IS NULL THEN github_repository_id ELSE NULL END
    ) VIRTUAL,

    CONSTRAINT fk_project_group_github_repository_project_group
        FOREIGN KEY (project_group_id) REFERENCES project_group (id),

    CONSTRAINT fk_project_group_github_repository_github_installation
        FOREIGN KEY (github_installation_id) REFERENCES github_installation (id),

    CONSTRAINT uq_project_group_github_repository_active_repository
        UNIQUE (active_project_group_id, active_github_repository_id)
);
