package team.po.feature.match.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.match.domain.MatchingMember;

@DataJpaTest
@ActiveProfiles("h2")
class MatchingMemberRepositoryTest {

	@Autowired
	private MatchingMemberRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findCurrentActiveByUserId_ignoresMembersFromDeletedSessions() {
		insertUser(1L);
		insertProjectRequest(10L, 1L, "MATCHED");
		insertProjectRequest(20L, 1L, "MATCHING");
		insertMatchingSession(100L, Instant.parse("2026-06-16T08:00:00Z"));
		insertMatchingSession(200L, null);
		insertMatchingMember(1000L, 100L, 10L, null);
		insertMatchingMember(2000L, 200L, 20L, null);
		entityManager.flush();
		entityManager.clear();

		Optional<MatchingMember> result = repository.findCurrentActiveByUserId(1L);

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(2000L);
		assertThat(result.get().getMatchingSession().getId()).isEqualTo(200L);
		assertThat(result.get().getProjectRequest().getId()).isEqualTo(20L);
	}

	@Test
	void findCurrentActiveByUserId_returnsEmptyWhenOnlyDeletedSessionMemberExists() {
		insertUser(1L);
		insertProjectRequest(10L, 1L, "MATCHED");
		insertMatchingSession(100L, Instant.parse("2026-06-16T08:00:00Z"));
		insertMatchingMember(1000L, 100L, 10L, null);
		entityManager.flush();
		entityManager.clear();

		Optional<MatchingMember> result = repository.findCurrentActiveByUserId(1L);

		assertThat(result).isEmpty();
	}

	private void insertUser(Long id) {
		entityManager.createNativeQuery("""
			INSERT INTO users (
				id, email, nickname, temperature, level, is_github_login, created_at
			) VALUES (
				:id, :email, :nickname, 50, 1, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("email", "user" + id + "@example.com")
			.setParameter("nickname", "user" + id)
			.executeUpdate();
	}

	private void insertProjectRequest(Long id, Long userId, String status) {
		entityManager.createNativeQuery("""
			INSERT INTO project_request (
				id,
				user_id,
				role,
				project_title,
				project_description,
				project_mvp,
				status,
				created_at
			) VALUES (
				:id,
				:userId,
				'BACKEND',
				NULL,
				NULL,
				NULL,
				:status,
				CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("status", status)
			.executeUpdate();
	}

	private void insertMatchingSession(Long id, Instant deletedAt) {
		entityManager.createNativeQuery("""
			INSERT INTO matching_session (
				id,
				created_at,
				deleted_at
			) VALUES (
				:id,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}

	private void insertMatchingMember(Long id, Long sessionId, Long projectRequestId, Instant deletedAt) {
		entityManager.createNativeQuery("""
			INSERT INTO matching_member (
				id,
				matching_session_id,
				project_request_id,
				is_accepted,
				created_at,
				deleted_at
			) VALUES (
				:id,
				:sessionId,
				:projectRequestId,
				NULL,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("sessionId", sessionId)
			.setParameter("projectRequestId", projectRequestId)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}
}
