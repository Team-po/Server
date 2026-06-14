package team.po.feature.teamspace.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.user.domain.Users;

@Entity
@Table(
	name = "weekly_github_summary",
	uniqueConstraints = @UniqueConstraint(
		name = "uq_weekly_github_summary_member_period",
		columnNames = {"project_group_member_id", "period_start", "period_end"}
	)
)
@NoArgsConstructor
@Getter
public class WeeklyGithubSummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_member_id", nullable = false)
	private ProjectGroupMember projectGroupMember;

	@Column(name = "period_start", nullable = false)
	private Instant periodStart;

	@Column(name = "period_end", nullable = false)
	private Instant periodEnd;

	@Lob
	@Column(name = "summary_json", nullable = false)
	private String summaryJson;

	@Column(name = "source_pr_count", nullable = false)
	private int sourcePrCount;

	@Column(name = "source_issue_count", nullable = false)
	private int sourceIssueCount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "generated_by_user_id", nullable = false)
	private Users generatedBy;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	@Builder
	public WeeklyGithubSummary(
		ProjectGroupMember projectGroupMember,
		Instant periodStart,
		Instant periodEnd,
		String summaryJson,
		int sourcePrCount,
		int sourceIssueCount,
		Users generatedBy,
		Instant generatedAt
	) {
		this.projectGroupMember = projectGroupMember;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.summaryJson = summaryJson;
		this.sourcePrCount = sourcePrCount;
		this.sourceIssueCount = sourceIssueCount;
		this.generatedBy = generatedBy;
		this.generatedAt = generatedAt;
	}

	public void updateSummary(
		String summaryJson,
		int sourcePrCount,
		int sourceIssueCount,
		Users generatedBy,
		Instant generatedAt
	) {
		this.summaryJson = summaryJson;
		this.sourcePrCount = sourcePrCount;
		this.sourceIssueCount = sourceIssueCount;
		this.generatedBy = generatedBy;
		this.generatedAt = generatedAt;
	}
}
