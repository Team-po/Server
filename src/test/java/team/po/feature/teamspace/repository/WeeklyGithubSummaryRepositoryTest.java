package team.po.feature.teamspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.teamspace.domain.WeeklyGithubSummary;
import team.po.feature.user.domain.Users;

@DataJpaTest
@ActiveProfiles("h2")
class WeeklyGithubSummaryRepositoryTest {

	@Autowired
	private WeeklyGithubSummaryRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findByProjectGroupMemberIdAndPeriod_returnsWeeklyGithubSummary() {
		Instant periodStart = Instant.parse("2026-05-25T00:00:00Z");
		Instant periodEnd = Instant.parse("2026-06-01T00:00:00Z");
		insertUser(1L, "member@example.com", "member");
		insertProjectGroup(10L);
		insertProjectGroupMember(100L, 1L, 10L);
		insertWeeklyGithubSummary(1000L, 100L, 1L, periodStart, periodEnd);
		entityManager.flush();
		entityManager.clear();

		WeeklyGithubSummary summary = repository
			.findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(100L, periodStart, periodEnd)
			.orElseThrow();

		assertThat(summary.getId()).isEqualTo(1000L);
		assertThat(summary.getProjectGroupMember().getId()).isEqualTo(100L);
		assertThat(summary.getPeriodStart()).isEqualTo(periodStart);
		assertThat(summary.getPeriodEnd()).isEqualTo(periodEnd);
		assertThat(summary.getSummaryJson()).isEqualTo("{\"summary\":\"이번 주 활동 요약\"}");
		assertThat(summary.getSourcePrCount()).isEqualTo(3);
		assertThat(summary.getSourceIssueCount()).isEqualTo(2);
		assertThat(summary.getGeneratedBy().getId()).isEqualTo(1L);
	}

	@Test
	void findByProjectGroupMemberIdAndPeriod_returnsEmpty_whenPeriodIsDifferent() {
		Instant periodStart = Instant.parse("2026-05-25T00:00:00Z");
		Instant periodEnd = Instant.parse("2026-06-01T00:00:00Z");
		insertUser(1L, "member@example.com", "member");
		insertProjectGroup(10L);
		insertProjectGroupMember(100L, 1L, 10L);
		insertWeeklyGithubSummary(1000L, 100L, 1L, periodStart, periodEnd);
		entityManager.flush();
		entityManager.clear();

		assertThat(repository.findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(
			100L,
			Instant.parse("2026-06-01T00:00:00Z"),
			Instant.parse("2026-06-08T00:00:00Z")
		)).isEmpty();
	}

	@Test
	void findByIdAndProjectGroupMemberId_returnsWeeklyGithubSummary() {
		Instant periodStart = Instant.parse("2026-05-25T00:00:00Z");
		Instant periodEnd = Instant.parse("2026-06-01T00:00:00Z");
		insertUser(1L, "member@example.com", "member");
		insertProjectGroup(10L);
		insertProjectGroupMember(100L, 1L, 10L);
		insertWeeklyGithubSummary(1000L, 100L, 1L, periodStart, periodEnd);
		entityManager.flush();
		entityManager.clear();

		WeeklyGithubSummary summary = repository.findByIdAndProjectGroupMember_Id(1000L, 100L)
			.orElseThrow();

		assertThat(summary.getId()).isEqualTo(1000L);
		assertThat(summary.getProjectGroupMember().getId()).isEqualTo(100L);
	}

	@Test
	void findByIdAndProjectGroupMemberId_returnsEmpty_whenProjectGroupMemberIsDifferent() {
		Instant periodStart = Instant.parse("2026-05-25T00:00:00Z");
		Instant periodEnd = Instant.parse("2026-06-01T00:00:00Z");
		insertUser(1L, "member@example.com", "member");
		insertUser(2L, "other@example.com", "other");
		insertProjectGroup(10L);
		insertProjectGroupMember(100L, 1L, 10L);
		insertProjectGroupMember(200L, 2L, 10L);
		insertWeeklyGithubSummary(1000L, 100L, 1L, periodStart, periodEnd);
		entityManager.flush();
		entityManager.clear();

		assertThat(repository.findByIdAndProjectGroupMember_Id(1000L, 200L)).isEmpty();
	}

	@Test
	void save_persistsUpdatedWeeklyGithubSummary() {
		Instant periodStart = Instant.parse("2026-05-25T00:00:00Z");
		Instant periodEnd = Instant.parse("2026-06-01T00:00:00Z");
		insertUser(1L, "member@example.com", "member");
		insertProjectGroup(10L);
		insertProjectGroupMember(100L, 1L, 10L);
		insertWeeklyGithubSummary(1000L, 100L, 1L, periodStart, periodEnd);
		entityManager.flush();
		entityManager.clear();

		Users generatedBy = entityManager.getReference(Users.class, 1L);
		WeeklyGithubSummary summary = repository
			.findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(100L, periodStart, periodEnd)
			.orElseThrow();
		summary.updateSummary(
			"{\"summary\":\"수정된 요약\"}",
			1,
			0,
			generatedBy,
			Instant.parse("2026-06-02T00:00:00Z")
		);

		repository.saveAndFlush(summary);
		entityManager.clear();

		WeeklyGithubSummary found = repository
			.findByProjectGroupMember_IdAndPeriodStartAndPeriodEnd(100L, periodStart, periodEnd)
			.orElseThrow();
		assertThat(found.getId()).isEqualTo(1000L);
		assertThat(found.getSummaryJson()).isEqualTo("{\"summary\":\"수정된 요약\"}");
		assertThat(found.getSourcePrCount()).isEqualTo(1);
		assertThat(found.getSourceIssueCount()).isZero();
		assertThat(found.getGeneratedAt()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
	}

	private void insertUser(Long id, String email, String nickname) {
		entityManager.createNativeQuery("""
			INSERT INTO users (
				id, email, nickname, temperature, level, is_github_login, created_at
			) VALUES (
				:id, :email, :nickname, 50, 1, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("email", email)
			.setParameter("nickname", nickname)
			.executeUpdate();
	}

	private void insertProjectGroup(Long id) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group (
				id, project_name, project_title, status, created_at
			) VALUES (
				:id, 'TeamPo', 'TeamPo', 'ACTIVE', CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.executeUpdate();
	}

	private void insertProjectGroupMember(Long id, Long userId, Long projectGroupId) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_member (
				id, user_id, project_group_id, role, group_role, is_admin, is_finish_agreed, created_at
			) VALUES (
				:id, :userId, :projectGroupId, 'BACKEND', 'MEMBER', false, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("projectGroupId", projectGroupId)
			.executeUpdate();
	}

	private void insertWeeklyGithubSummary(
		Long id,
		Long projectGroupMemberId,
		Long generatedByUserId,
		Instant periodStart,
		Instant periodEnd
	) {
		entityManager.createNativeQuery("""
			INSERT INTO weekly_github_summary (
				id,
				project_group_member_id,
				period_start,
				period_end,
				summary_json,
				source_pr_count,
				source_issue_count,
				generated_by_user_id,
				generated_at,
				created_at,
				updated_at
			) VALUES (
				:id,
				:projectGroupMemberId,
				:periodStart,
				:periodEnd,
				:summaryJson,
				3,
				2,
				:generatedByUserId,
				:generatedAt,
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("projectGroupMemberId", projectGroupMemberId)
			.setParameter("periodStart", periodStart)
			.setParameter("periodEnd", periodEnd)
			.setParameter("summaryJson", "{\"summary\":\"이번 주 활동 요약\"}")
			.setParameter("generatedByUserId", generatedByUserId)
			.setParameter("generatedAt", Instant.parse("2026-06-01T00:00:00Z"))
			.executeUpdate();
	}
}
