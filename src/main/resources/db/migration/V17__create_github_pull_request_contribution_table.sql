CREATE TABLE github_pull_request_contribution
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_id      BIGINT        NOT NULL,
    github_repository_id  BIGINT        NOT NULL,
    github_pr_id          BIGINT        NOT NULL,
    pr_number             BIGINT        NOT NULL,
    title                 VARCHAR(512)  NOT NULL,
    author_github_user_id BIGINT        NOT NULL,
    author_github_username VARCHAR(255) NOT NULL,
    state                 VARCHAR(30)   NOT NULL,
    merged                BOOLEAN       NOT NULL DEFAULT FALSE,
    merged_at             TIMESTAMP(6)  NULL,
    additions             INT           NOT NULL DEFAULT 0,
    deletions             INT           NOT NULL DEFAULT 0,
    changed_files         INT           NOT NULL DEFAULT 0,
    linked_issue_count    INT           NOT NULL DEFAULT 0,
    html_url              VARCHAR(2048) NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    synced_at             TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_github_pull_request_contribution_project_group
        FOREIGN KEY (project_group_id) REFERENCES project_group (id),

    CONSTRAINT uq_github_pull_request_contribution_repository_pr
        UNIQUE (project_group_id, github_repository_id, github_pr_id),

    CONSTRAINT ck_github_pull_request_contribution_additions
        CHECK (additions >= 0),

    CONSTRAINT ck_github_pull_request_contribution_deletions
        CHECK (deletions >= 0),

    CONSTRAINT ck_github_pull_request_contribution_changed_files
        CHECK (changed_files >= 0),

    CONSTRAINT ck_github_pull_request_contribution_linked_issue_count
        CHECK (linked_issue_count >= 0)
);

CREATE INDEX idx_github_pull_request_contribution_project_group_repository
    ON github_pull_request_contribution (project_group_id, github_repository_id);

CREATE INDEX idx_github_pull_request_contribution_author
    ON github_pull_request_contribution (author_github_user_id);

CREATE INDEX idx_github_pull_request_contribution_merged_at
    ON github_pull_request_contribution (merged_at);
