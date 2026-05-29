package team.po.feature.checklist.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.user.domain.Users;

@Entity
@Table(name = "project_checklist")
@NoArgsConstructor
@Getter
public class ProjectChecklist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@Column(nullable = false, length = 255)
	private String title;

	@Lob
	@Column(name = "description")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProjectChecklistStatus status;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Lob
	@Column(name = "ai_advice")
	private String aiAdvice;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_user_id")
	private Users assignee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	private Users createdBy;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public ProjectChecklist(
		ProjectGroup projectGroup,
		String title,
		String description,
		ProjectChecklistStatus status,
		LocalDate dueDate,
		String aiAdvice,
		Users assignee,
		Users createdBy
	) {
		this.projectGroup = projectGroup;
		this.title = title;
		this.description = description;
		this.status = status;
		this.dueDate = dueDate;
		this.aiAdvice = aiAdvice;
		this.assignee = assignee;
		this.createdBy = createdBy;
	}

	public void update(
		String title,
		String description,
		ProjectChecklistStatus status,
		LocalDate dueDate,
		Users assignee
	) {
		boolean descriptionChanged = !Objects.equals(this.description, description);

		this.title = title;
		this.description = description;
		this.status = status;
		this.dueDate = dueDate;
		this.assignee = assignee;

		if (descriptionChanged) {
			this.aiAdvice = null;
		}
	}

	public void updateAiAdvice(String aiAdvice) {
		this.aiAdvice = aiAdvice;
	}
}
