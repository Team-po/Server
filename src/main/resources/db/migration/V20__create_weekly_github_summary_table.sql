CREATE TABLE weekly_github_summary
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_group_member_id BIGINT    NOT NULL,
    period_start         TIMESTAMP(6) NOT NULL,
    period_end           TIMESTAMP(6) NOT NULL,
    summary_json         TEXT         NOT NULL,
    source_pr_count      INT          NOT NULL DEFAULT 0,
    source_issue_count   INT          NOT NULL DEFAULT 0,
    generated_by_user_id BIGINT       NOT NULL,
    generated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_weekly_github_summary_project_group_member
        FOREIGN KEY (project_group_member_id) REFERENCES project_group_member (id),

    CONSTRAINT fk_weekly_github_summary_generated_by
        FOREIGN KEY (generated_by_user_id) REFERENCES users (id),

    CONSTRAINT uq_weekly_github_summary_member_period
        UNIQUE (project_group_member_id, period_start, period_end),

    CONSTRAINT ck_weekly_github_summary_period
        CHECK (period_end > period_start),

    CONSTRAINT ck_weekly_github_summary_source_pr_count
        CHECK (source_pr_count >= 0),

    CONSTRAINT ck_weekly_github_summary_source_issue_count
        CHECK (source_issue_count >= 0)
);

CREATE INDEX idx_weekly_github_summary_member_generated_at
    ON weekly_github_summary (project_group_member_id, generated_at);

CREATE INDEX idx_weekly_github_summary_generated_by_user_id
    ON weekly_github_summary (generated_by_user_id);
