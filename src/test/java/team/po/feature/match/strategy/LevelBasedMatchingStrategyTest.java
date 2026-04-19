package team.po.feature.match.strategy;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;
import team.po.feature.user.domain.Users;

class LevelBasedMatchingStrategyTest {

	private LevelBasedMatchingStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new LevelBasedMatchingStrategy(new DefaultMatchingScorer());
	}

	private Users createUser(Long id, int level, int temperature) {
		Users user = Users.builder()
			.email("test" + id + "@email.com")
			.password("password")
			.nickname("user" + id)
			.level(level)
			.temperature(temperature)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private ProjectRequest createRequest(Long id, Users user, Role role,
		String title, String desc, String mvp) {
		ProjectRequest pr = ProjectRequest.builder()
			.user(user)
			.role(role)
			.projectTitle(title)
			.projectDescription(desc)
			.projectMvp(mvp)
			.build();
		ReflectionTestUtils.setField(pr, "id", id);
		ReflectionTestUtils.setField(pr, "createdAt", Instant.now()); // 추가
		return pr;
	}

	@Test
	void findCandidates_success() {
		Users hostUser = createUser(1L, 5, 50);
		ProjectRequest host = createRequest(1L, hostUser, Role.BACKEND, "팀포", "설명", "MVP");

		Users b2 = createUser(2L, 5, 50);
		Users fe = createUser(3L, 5, 50);
		Users de = createUser(4L, 5, 50);

		ProjectRequest backendPr = createRequest(2L, b2, Role.BACKEND, null, null, null);
		ProjectRequest frontendPr = createRequest(3L, fe, Role.FRONTEND, null, null, null);
		ProjectRequest designPr = createRequest(4L, de, Role.DESIGN, null, null, null);

		Map<Role, List<ProjectRequest>> pool = Map.of(
			Role.BACKEND, List.of(backendPr),
			Role.FRONTEND, List.of(frontendPr),
			Role.DESIGN, List.of(designPr)
		);

		MatchingContext context = new MatchingContext(host, pool, Set.of(hostUser.getId()));
		Optional<MatchingResult> result = strategy.findCandidates(context);

		assertThat(result).isPresent();
		assertThat(result.get().selectedCandidates()).hasSize(3);
	}

	@Test
	void findCandidates_empty_whenCandidatesInsufficient() {
		Users hostUser = createUser(1L, 5, 50);
		ProjectRequest host = createRequest(1L, hostUser, Role.BACKEND, "팀포", "설명", "MVP");

		Map<Role, List<ProjectRequest>> pool = Map.of(
			Role.BACKEND, List.of(),
			Role.FRONTEND, List.of(),
			Role.DESIGN, List.of()
		);

		MatchingContext context = new MatchingContext(host, pool, Set.of());
		Optional<MatchingResult> result = strategy.findCandidates(context);

		assertThat(result).isEmpty();
	}

	@Test
	void findCandidates_excludesBlacklist() {
		Users hostUser = createUser(1L, 5, 50);
		ProjectRequest host = createRequest(1L, hostUser, Role.BACKEND, "팀포", "설명", "MVP");

		Users blacklistedUser = createUser(2L, 5, 50);
		ProjectRequest blacklistedPr = createRequest(2L, blacklistedUser, Role.BACKEND, null, null, null);

		Map<Role, List<ProjectRequest>> pool = Map.of(
			Role.BACKEND, List.of(blacklistedPr),
			Role.FRONTEND, List.of(),
			Role.DESIGN, List.of()
		);

		MatchingContext context = new MatchingContext(host, pool, Set.of(blacklistedUser.getId()));
		Optional<MatchingResult> result = strategy.findCandidates(context);

		assertThat(result).isEmpty();
	}

	@Test
	void findCandidates_excludesLevelOutOfRange() {
		Users hostUser = createUser(1L, 5, 50);
		ProjectRequest host = createRequest(1L, hostUser, Role.BACKEND, "팀포", "설명", "MVP");

		Users farLevelUser = createUser(2L, 10, 50); // 레벨 차이 5 — 범위 초과
		ProjectRequest farPr = createRequest(2L, farLevelUser, Role.BACKEND, null, null, null);

		Map<Role, List<ProjectRequest>> pool = Map.of(
			Role.BACKEND, List.of(farPr),
			Role.FRONTEND, List.of(),
			Role.DESIGN, List.of()
		);

		MatchingContext context = new MatchingContext(host, pool, Set.of());
		Optional<MatchingResult> result = strategy.findCandidates(context);

		assertThat(result).isEmpty();
	}

	@Test
	void findCandidates_sortsHigherTemperatureFirst() {
		Users hostUser = createUser(1L, 5, 50);
		ProjectRequest host = createRequest(1L, hostUser, Role.BACKEND, "팀포", "설명", "MVP");

		Users lowTemp = createUser(2L, 5, 30);
		Users highTemp = createUser(3L, 5, 80);

		ProjectRequest lowPr = createRequest(2L, lowTemp, Role.BACKEND, null, null, null);
		ProjectRequest highPr = createRequest(3L, highTemp, Role.BACKEND, null, null, null);

		Users fe = createUser(4L, 5, 50);
		Users de = createUser(5L, 5, 50);
		ProjectRequest frontendPr = createRequest(4L, fe, Role.FRONTEND, null, null, null);
		ProjectRequest designPr = createRequest(5L, de, Role.DESIGN, null, null, null);

		Map<Role, List<ProjectRequest>> pool = Map.of(
			Role.BACKEND, List.of(lowPr, highPr),
			Role.FRONTEND, List.of(frontendPr),
			Role.DESIGN, List.of(designPr)
		);

		MatchingContext context = new MatchingContext(host, pool, Set.of(hostUser.getId()));
		Optional<MatchingResult> result = strategy.findCandidates(context);

		assertThat(result).isPresent();
		assertThat(result.get().selectedCandidates())
			.filteredOn(pr -> pr.getRole() == Role.BACKEND)
			.first()
			.extracting(pr -> pr.getUser().getId())
			.isEqualTo(3L); // highTemp 선택
	}
}