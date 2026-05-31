package team.po.feature.teamspace.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.projectgroup.domain.ProjectGroup;

@Entity
@Table(name = "github_pull_request_contribution")
@NoArgsConstructor
@Getter
public class GithubPullRequestContribution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@Column(name = "github_repository_id", nullable = false)
	private Long githubRepositoryId;

	@Column(name = "github_pr_id", nullable = false)
	private Long githubPrId;

	@Column(name = "pr_number", nullable = false)
	private Long prNumber;

	@Column(name = "title", nullable = false, length = 512)
	private String title;

	@Column(name = "author_github_user_id", nullable = false)
	private Long authorGithubUserId;

	@Column(name = "author_github_username", nullable = false)
	private String authorGithubUsername;

	@Column(name = "state", nullable = false)
	private String state;

	@Column(name = "merged", nullable = false)
	private boolean merged;

	@Column(name = "merged_at")
	private Instant mergedAt;

	@Column(name = "additions", nullable = false)
	private int additions;

	@Column(name = "deletions", nullable = false)
	private int deletions;

	@Column(name = "changed_files", nullable = false)
	private int changedFiles;

	@Column(name = "linked_issue_count", nullable = false)
	private int linkedIssueCount;

	@Column(name = "html_url", nullable = false, length = 2048)
	private String htmlUrl;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	@Column(name = "synced_at", nullable = false)
	private Instant syncedAt;

	@Builder
	public GithubPullRequestContribution(
		ProjectGroup projectGroup,
		Long githubRepositoryId,
		Long githubPrId,
		Long prNumber,
		String title,
		Long authorGithubUserId,
		String authorGithubUsername,
		String state,
		boolean merged,
		Instant mergedAt,
		int additions,
		int deletions,
		int changedFiles,
		int linkedIssueCount,
		String htmlUrl,
		Instant syncedAt
	) {
		this.projectGroup = projectGroup;
		this.githubRepositoryId = githubRepositoryId;
		this.githubPrId = githubPrId;
		this.prNumber = prNumber;
		this.title = title;
		this.authorGithubUserId = authorGithubUserId;
		this.authorGithubUsername = authorGithubUsername;
		this.state = state;
		this.merged = merged;
		this.mergedAt = mergedAt;
		this.additions = additions;
		this.deletions = deletions;
		this.changedFiles = changedFiles;
		this.linkedIssueCount = linkedIssueCount;
		this.htmlUrl = htmlUrl;
		this.syncedAt = syncedAt;
	}

}
