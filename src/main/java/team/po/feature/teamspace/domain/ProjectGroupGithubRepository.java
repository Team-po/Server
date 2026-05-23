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
@Table(name = "project_group_github_repository")
@NoArgsConstructor
@Getter
public class ProjectGroupGithubRepository {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "github_installation_id", nullable = false)
	private GithubInstallation githubInstallation;

	@Column(name = "github_repository_id", nullable = false)
	private Long githubRepositoryId;

	@Column(name = "owner", nullable = false)
	private String owner;

	@Column(name = "repo_name", nullable = false)
	private String repoName;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(name = "default_branch")
	private String defaultBranch;

	@Column(name = "is_private", nullable = false)
	private boolean privateRepository;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Builder
	public ProjectGroupGithubRepository(
		ProjectGroup projectGroup,
		GithubInstallation githubInstallation,
		Long githubRepositoryId,
		String owner,
		String repoName,
		String fullName,
		String defaultBranch,
		boolean privateRepository
	) {
		this.projectGroup = projectGroup;
		this.githubInstallation = githubInstallation;
		this.githubRepositoryId = githubRepositoryId;
		this.owner = owner;
		this.repoName = repoName;
		this.fullName = fullName;
		this.defaultBranch = defaultBranch;
		this.privateRepository = privateRepository;
	}

	public void softDelete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
