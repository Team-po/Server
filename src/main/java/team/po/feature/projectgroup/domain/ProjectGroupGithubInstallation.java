package team.po.feature.projectgroup.domain;

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
import team.po.feature.user.domain.Users;

@Entity
@Table(name = "project_group_github_installation")
@NoArgsConstructor
@Getter
public class ProjectGroupGithubInstallation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "github_installation_id", nullable = false)
	private GithubInstallation githubInstallation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "connected_by", nullable = false)
	private Users connectedBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Builder
	public ProjectGroupGithubInstallation(
		ProjectGroup projectGroup,
		GithubInstallation githubInstallation,
		Users connectedBy
	) {
		this.projectGroup = projectGroup;
		this.githubInstallation = githubInstallation;
		this.connectedBy = connectedBy;
	}

	public void softDelete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
