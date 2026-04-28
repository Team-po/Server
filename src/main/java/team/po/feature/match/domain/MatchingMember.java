package team.po.feature.match.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "matching_member")
@NoArgsConstructor
@Getter
public class MatchingMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "matching_session_id", nullable = false)
	private Long matchingSessionId;

	@Column(name = "project_request_id", nullable = false)
	private Long projectRequestId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

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
	private MatchingMember(Long matchingSessionId, Long projectRequestId, Long userId, Boolean isAccepted) {
		this.matchingSessionId = matchingSessionId;
		this.projectRequestId = projectRequestId;
		this.userId = userId;
		this.isAccepted = isAccepted;
	}

	// 팀장은 수락으로 간주
	public static MatchingMember createForHost(Long matchingSessionId, Long projectRequestId, Long userId) {
		return MatchingMember.builder()
			.matchingSessionId(matchingSessionId)
			.projectRequestId(projectRequestId)
			.userId(userId)
			.isAccepted(true)
			.build();
	}

	public static MatchingMember createForMember(Long matchingSessionId, Long projectRequestId, Long userId) {
		return MatchingMember.builder()
			.matchingSessionId(matchingSessionId)
			.projectRequestId(projectRequestId)
			.userId(userId)
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
}
