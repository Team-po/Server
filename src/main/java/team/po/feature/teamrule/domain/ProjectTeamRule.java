package team.po.feature.teamrule.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.user.domain.Users;

@Entity
@Table(name = "project_team_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProjectTeamRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@Lob
	@Column(name = "content_md", nullable = false)
	private String content;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	private Users createdBy;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "updated_by_user_id", nullable = false)
	private Users updatedBy;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	private ProjectTeamRule(
		ProjectGroup projectGroup,
		String content,
		Users createdBy,
		Users updatedBy
	) {
		this.projectGroup = projectGroup;
		this.content = content;
		this.createdBy = createdBy;
		this.updatedBy = updatedBy;
	}

	public static ProjectTeamRule createDefault(ProjectGroup projectGroup, Users requester) {
		return ProjectTeamRule.builder()
			.projectGroup(projectGroup)
			.content(TeamRuleTemplate.DEFAULT_CONTENT)
			.createdBy(requester)
			.updatedBy(requester)
			.build();
	}

	public void update(String content, Users updater) {
		this.content = content;
		this.updatedBy = updater;
	}

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
