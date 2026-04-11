package team.po.feature.projectgroup.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_group")
@NoArgsConstructor
@Getter
public class ProjectGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_name", nullable = false)
	private String projectName;

	@Column(name = "project_title", nullable = false)
	private String projectTitle;

	@Column(name = "group_id", nullable = false, unique = true)
	private Long groupId;

	@Column(name = "project_description")
	private String projectDescription;

	@Column(name = "project_mvp")
	private String projectMvp;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProjectGroupStatus status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public ProjectGroup(
		String projectName,
		String projectTitle,
		Long groupId,
		String projectDescription,
		String projectMvp,
		ProjectGroupStatus status
	) {
		this.projectName = projectName;
		this.projectTitle = projectTitle;
		this.groupId = groupId;
		this.projectDescription = projectDescription;
		this.projectMvp = projectMvp;
		this.status = status;
	}
}
