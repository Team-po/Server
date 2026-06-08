package team.po.feature.teamspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomExceptionHandler;
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.dto.GetAvailableGithubRepositoryList;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.dto.GetGithubRepositoryContributionResponse;
import team.po.feature.teamspace.dto.GetGithubRepositoryListResponse;
import team.po.feature.teamspace.dto.GetWeeklyGithubSummaryListResponse;
import team.po.feature.teamspace.dto.GetWeeklyGithubSummaryResponse;
import team.po.feature.teamspace.dto.GithubWeeklySummaryContent;
import team.po.feature.teamspace.dto.SetGithubRepositoryListRequest;
import team.po.feature.teamspace.service.TeamspaceService;
import team.po.feature.user.domain.Users;

@WebMvcTest(TeamspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class TeamspaceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TeamspaceService teamspaceService;

	@MockitoBean
	private LoginUserArgumentResolver loginUserArgumentResolver;

	private Users mockUser;

	@BeforeEach
	void setUp() throws Exception {
		mockUser = Users.builder()
			.email("tester@example.com")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(mockUser, "id", 1L);

		when(loginUserArgumentResolver.supportsParameter(any())).thenReturn(true);
		when(loginUserArgumentResolver.resolveArgument(any(), any(), any(), any())).thenReturn(mockUser);

		UserPrincipal principal = new UserPrincipal(1L, "tester@example.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getGithubInstallationStatus_returnsOk() throws Exception {
		when(teamspaceService.getGithubInstallationStatus(10L, 1L))
			.thenReturn(GetGithubInstallationStatusResponse.connected("student-team-org", 2L));

		mockMvc.perform(get("/api/team-space/10/github/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(true))
			.andExpect(jsonPath("$.organizationLogin").value("student-team-org"))
			.andExpect(jsonPath("$.repositoryCount").value(2));
	}

	@Test
	void createGithubAppInstallationUrl_returnsOk() throws Exception {
		when(teamspaceService.createGithubAppInstallationUrl(mockUser, 10L))
			.thenReturn(new CreateGithubAppInstallationUrlResponse(
				"https://github.com/apps/teampo-dev/installations/new?state=test-state"
			));

		mockMvc.perform(post("/api/team-space/10/github/install-url"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.installUrl")
				.value("https://github.com/apps/teampo-dev/installations/new?state=test-state"));
	}

	@Test
	void completeGithubAppInstallation_returnsOk() throws Exception {
		mockMvc.perform(post("/api/team-space/10/github/installations/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "installationId": 12345,
					  "setupAction": "install",
					  "state": "test-state"
					}
					"""))
			.andExpect(status().isOk());

		verify(teamspaceService).completeGithubAppInstallation(
			new CompleteGithubAppInstallationRequest(12345L, "install", "test-state"),
			10L,
			1L
		);
	}

	@Test
	void getGithubRepositoryList_returnsOk() throws Exception {
		when(teamspaceService.getAvailableGithubRepositoryList(mockUser, 10L))
			.thenReturn(new GetAvailableGithubRepositoryList(List.of(
				new GetAvailableGithubRepositoryList.RepositoryResponse(
					100L,
					"backend",
					"student-team-org/backend"
				)
			)));

		mockMvc.perform(get("/api/team-space/10/github/available-repositories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.repositories[0].githubRepositoryId").value(100))
			.andExpect(jsonPath("$.repositories[0].repoName").value("backend"))
			.andExpect(jsonPath("$.repositories[0].fullName").value("student-team-org/backend"));
	}

	@Test
	void getRegisteredGithubRepositoryList_returnsOk() throws Exception {
		when(teamspaceService.getGithubRepositoryList(mockUser, 10L))
			.thenReturn(new GetGithubRepositoryListResponse(List.of(
				new GetGithubRepositoryListResponse.RepositoryResponse(
					100L,
					"backend",
					"student-team-org/backend"
				)
			)));

		mockMvc.perform(get("/api/team-space/10/github/repositories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.repositories[0].githubRepositoryId").value(100))
			.andExpect(jsonPath("$.repositories[0].repoName").value("backend"))
			.andExpect(jsonPath("$.repositories[0].fullName").value("student-team-org/backend"));
	}

	@Test
	void setGithubRepositoryList_returnsOk() throws Exception {
		mockMvc.perform(put("/api/team-space/10/github/repositories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "githubRepositoryIds": [100, 200]
					}
					"""))
			.andExpect(status().isOk());

		verify(teamspaceService).setGithubRepositoryList(
			mockUser,
			10L,
			new SetGithubRepositoryListRequest(List.of(100L, 200L))
		);
	}

	@Test
	void setGithubRepositoryList_returnsOk_whenRepositoryIdsAreEmpty() throws Exception {
		mockMvc.perform(put("/api/team-space/10/github/repositories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "githubRepositoryIds": []
					}
					"""))
			.andExpect(status().isOk());

		verify(teamspaceService).setGithubRepositoryList(
			mockUser,
			10L,
			new SetGithubRepositoryListRequest(List.of())
		);
	}

	@Test
	void getGithubRepositoryContributions_returnsOk() throws Exception {
		when(teamspaceService.getGithubRepositoryContributions(mockUser, 10L, 100L))
			.thenReturn(new GetGithubRepositoryContributionResponse(
				100L,
				"backend",
				"student-team-org/backend",
				List.of(new GetGithubRepositoryContributionResponse.ContributorResponse(
					1L,
					501L,
					"dev-a",
					3L,
					2L,
					120L,
					15L,
					8L,
					40L
				))
			));

		mockMvc.perform(get("/api/team-space/10/github/repositories/100/contributions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.githubRepositoryId").value(100))
			.andExpect(jsonPath("$.repoName").value("backend"))
			.andExpect(jsonPath("$.fullName").value("student-team-org/backend"))
			.andExpect(jsonPath("$.contributors[0].userId").value(1))
			.andExpect(jsonPath("$.contributors[0].githubUserId").value(501))
			.andExpect(jsonPath("$.contributors[0].githubUsername").value("dev-a"))
			.andExpect(jsonPath("$.contributors[0].mergedPrCount").value(3))
			.andExpect(jsonPath("$.contributors[0].linkedIssueCount").value(2))
			.andExpect(jsonPath("$.contributors[0].additions").value(120))
			.andExpect(jsonPath("$.contributors[0].deletions").value(15))
			.andExpect(jsonPath("$.contributors[0].changedFiles").value(8))
			.andExpect(jsonPath("$.contributors[0].contributionScore").value(40));

		verify(teamspaceService).getGithubRepositoryContributions(mockUser, 10L, 100L);
	}

	@Test
	void syncGithubPullRequestContributions_returnsOk() throws Exception {
		mockMvc.perform(post(
				"/api/team-space/10/github/repositories/100/pull-request-contributions/sync"
			))
			.andExpect(status().isOk());

		verify(teamspaceService).syncGithubPullRequestContributions(mockUser, 10L, 100L);
	}

	@Test
	void getWeeklyGithubSummaries_returnsOk() throws Exception {
		when(teamspaceService.getWeeklyGithubSummaries(mockUser, 10L, 2L))
			.thenReturn(new GetWeeklyGithubSummaryListResponse(
				List.of(new GetWeeklyGithubSummaryResponse(
					1000L,
					Instant.parse("2026-05-25T15:00:00Z"),
					Instant.parse("2026-06-01T15:00:00Z"),
					3,
					2,
					new GithubWeeklySummaryContent(
						"지난 주 Github 활동 요약",
						List.of("주간 요약 조회 API 구현"),
						List.of("PR #10 요약 조회 API 추가"),
						List.of("Issue #82 주간 요약 기능 정리"),
						List.of("프론트엔드 조회 화면 연동")
					)
				))
			));

		mockMvc.perform(get("/api/team-space/10/github/users/2/weekly-summaries"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summaries[0].weeklyGithubSummaryId").value(1000))
			.andExpect(jsonPath("$.summaries[0].periodStart").value("2026-05-25T15:00:00Z"))
			.andExpect(jsonPath("$.summaries[0].periodEnd").value("2026-06-01T15:00:00Z"))
			.andExpect(jsonPath("$.summaries[0].sourcePrCount").value(3))
			.andExpect(jsonPath("$.summaries[0].sourceIssueCount").value(2))
			.andExpect(jsonPath("$.summaries[0].summary.summary").value("지난 주 Github 활동 요약"))
			.andExpect(jsonPath("$.summaries[0].summary.mainActivities[0]").value("주간 요약 조회 API 구현"));

		verify(teamspaceService).getWeeklyGithubSummaries(mockUser, 10L, 2L);
	}

	@Test
	void getWeeklyGithubSummaries_returnsEmptyList() throws Exception {
		when(teamspaceService.getWeeklyGithubSummaries(mockUser, 10L, 2L))
			.thenReturn(new GetWeeklyGithubSummaryListResponse(
				List.of()
			));

		mockMvc.perform(get("/api/team-space/10/github/users/2/weekly-summaries"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summaries").isArray())
			.andExpect(jsonPath("$.summaries").isEmpty());

		verify(teamspaceService).getWeeklyGithubSummaries(mockUser, 10L, 2L);
	}
}
