package team.po.feature.match.domain;

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
@Table(name = "matching_member")
@NoArgsConstructor
@Getter
public class MatchingMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "matching_session_id", nullable = false)
	private MatchingSession matchingSession;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_request_id", nullable = false)
	private ProjectRequest projectRequest;

	// default: host - true, member - null
	@Column(name = "is_accepted")
	private Boolean isAccepted;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "responded_at")
	private Instant respondedAt;

	// 사용자가 취소 요청한 경우 soft delete
	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Builder
	private MatchingMember(MatchingSession matchingSession, ProjectRequest projectRequest,
		Boolean isAccepted) {
		this.matchingSession = matchingSession;
		this.projectRequest = projectRequest;
		this.isAccepted = isAccepted;
	}

	// 팀장은 수락으로 간주
	public static MatchingMember createForHost(MatchingSession session, ProjectRequest hostPr) {
		return MatchingMember.builder()
			.matchingSession(session)
			.projectRequest(hostPr)
			.isAccepted(true)
			.build();
	}

	public static MatchingMember createForMember(MatchingSession session, ProjectRequest memberPr) {
		return MatchingMember.builder()
			.matchingSession(session)
			.projectRequest(memberPr)
			.isAccepted(null)
			.build();
	}

	public void accept() {
		this.isAccepted = true;
		this.respondedAt = Instant.now();
	}

	public void reject() {
		this.isAccepted = false;
		this.respondedAt = Instant.now();
		// 거절 시 soft delete
		this.deletedAt = Instant.now();
	}

	// 사용자가 취소 요청한 경우 soft delete
	public void cancel() {
		this.deletedAt = Instant.now();
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}

	public Users getUser() {
		return this.projectRequest.getUser();
	}
}
