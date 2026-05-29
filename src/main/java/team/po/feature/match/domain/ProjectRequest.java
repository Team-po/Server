package team.po.feature.match.domain;

import java.time.Instant;

import org.springframework.util.StringUtils;

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

	// 유저가 직접 취소
	public void cancel() {
		if (this.status != Status.WAITING && this.status != Status.MATCHING) {
			throw new ApplicationException(ErrorCode.PROJECT_REQUEST_CANCEL_NOT_ALLOWED);
		}
		this.status = Status.CANCELED;
		this.canceledAt = Instant.now();
	}

	public boolean isHostRequest() {
		return StringUtils.hasText(projectTitle)
			&& StringUtils.hasText(projectMvp)
			&& StringUtils.hasText(projectDescription);
	}

	// MATCHING

	// 매칭 세션 배정
	public void startMatching() {
		if (this.status != Status.WAITING) {
			throw new ApplicationException(ErrorCode.INVALID_MATCH_STATUS);
		}
		this.status = Status.MATCHING;
	}

	// 모든 멤버가 수락을 완료한 경우
	public void complete() {
		if (this.status != Status.MATCHING) {
			throw new ApplicationException(ErrorCode.INVALID_MATCH_STATUS);
		}
		this.status = Status.MATCHED;
	}

	// 수락을 거절한 경우 | Host가 매칭을 취소한 경우
	public void resetToWaiting() {
		if (this.status != Status.MATCHING) {
			throw new ApplicationException(ErrorCode.INVALID_MATCH_STATUS);
		}
		this.status = Status.WAITING;
	}

}
