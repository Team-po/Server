package team.po.feature.match.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;
import team.po.feature.user.domain.Users;

@Entity
@Table(name = "project_request")
@NoArgsConstructor
@Getter
public class ProjectRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private Users user;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Column(name = "project_title")
	private String projectTitle;

	@Column(name = "project_description", columnDefinition = "TEXT")
	private String projectDescription;

	@Column(name = "project_mvp", columnDefinition = "TEXT")
	private String projectMvp;

	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "canceled_at")
	private Instant canceledAt;

	@Builder
	public ProjectRequest(Users user, Role role, String projectTitle,
		String projectDescription, String projectMvp) {
		this.user = user;
		this.role = role;
		this.projectTitle = projectTitle;
		this.projectDescription = projectDescription;
		this.projectMvp = projectMvp;
		this.status = Status.WAITING; // default
	}

	public void cancel() {
		if (this.status != Status.WAITING && this.status != Status.MATCHING) {
			throw new ApplicationException(ErrorCode.PROJECT_REQUEST_CANCEL_NOT_ALLOWED);
		}
		this.status = Status.CANCELED;
		this.canceledAt = Instant.now();
	}
}
