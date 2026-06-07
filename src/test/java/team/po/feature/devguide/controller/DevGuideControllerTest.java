package team.po.feature.devguide.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.exception.ApplicationException;
import team.po.exception.CustomExceptionHandler;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.domain.DevGuideStatus;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.dto.DevGuideQueryResponse;
import team.po.feature.devguide.dto.DevGuideRegenerateResponse;
import team.po.feature.devguide.dto.DevGuideVersionListResponse;
import team.po.feature.devguide.dto.DevGuideVersionResponse;
import team.po.feature.devguide.service.DevGuideService;
import team.po.feature.user.domain.Users;

@WebMvcTest(DevGuideController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class DevGuideControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevGuideService devGuideService;

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
	}

	// ─── GET /dev-guide ───────────────────────────────────────────────────────

	@Test
	void getDevGuide_returnsContentWithCompletedStatusAndRemainingCount() throws Exception {
		DevGuideContent content = sampleContent("프로젝트 개요입니다.");
		DevGuideQueryResponse response = new DevGuideQueryResponse(content, DevGuideStatus.COMPLETED, 2);

		when(devGuideService.getDevGuide(1L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.remainingRegenerationCount").value(2))
			.andExpect(jsonPath("$.overview").value("프로젝트 개요입니다."))
			.andExpect(jsonPath("$.techStack[0].category").value("Backend"));
	}

	@Test
	void getDevGuide_returnsContentWithGeneratingStatus_whenRegenerationInProgress() throws Exception {
		DevGuideContent content = sampleContent("기존 가이드 개요입니다.");
		DevGuideQueryResponse response = new DevGuideQueryResponse(content, DevGuideStatus.GENERATING, 3);

		when(devGuideService.getDevGuide(1L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationStatus").value("GENERATING"))
			.andExpect(jsonPath("$.overview").value("기존 가이드 개요입니다."));
	}

	@Test
	void getDevGuide_returnsGeneratingStatusWithoutContent() throws Exception {
		DevGuideQueryResponse response = new DevGuideQueryResponse(null, DevGuideStatus.GENERATING, null);

		when(devGuideService.getDevGuide(1L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationStatus").value("GENERATING"))
			.andExpect(jsonPath("$.content").doesNotExist());
	}

	@Test
	void getDevGuide_returnsFailedStatusWithoutContent() throws Exception {
		DevGuideQueryResponse response = new DevGuideQueryResponse(null, DevGuideStatus.FAILED, null);

		when(devGuideService.getDevGuide(1L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationStatus").value("FAILED"))
			.andExpect(jsonPath("$.content").doesNotExist());
	}

	// ─── GET /dev-guide/versions ─────────────────────────────────────────────

	@Test
	void getDevGuideVersions_returnsVersionList() throws Exception {
		DevGuideVersionListResponse response = new DevGuideVersionListResponse(List.of(
			new DevGuideVersionResponse(2L, 2, DevGuideGenerationType.MANUAL, true,
				LocalDateTime.of(2026, 6, 7, 12, 30)),
			new DevGuideVersionResponse(1L, 1, DevGuideGenerationType.INITIAL, false,
				LocalDateTime.of(2026, 6, 7, 12, 0))
		));

		when(devGuideService.getVersions(1L, 1L)).thenReturn(response);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide/versions", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.versions[0].devGuideId").value(2))
			.andExpect(jsonPath("$.versions[0].versionNo").value(2))
			.andExpect(jsonPath("$.versions[0].generationType").value("MANUAL"))
			.andExpect(jsonPath("$.versions[0].confirmed").value(true))
			.andExpect(jsonPath("$.versions[1].devGuideId").value(1))
			.andExpect(jsonPath("$.versions[1].versionNo").value(1))
			.andExpect(jsonPath("$.versions[1].generationType").value("INITIAL"))
			.andExpect(jsonPath("$.versions[1].confirmed").value(false));
	}

	@Test
	void getDevGuideVersions_returnsConflict_whenGuideIsGenerating() throws Exception {
		when(devGuideService.getVersions(1L, 1L))
			.thenThrow(new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING));

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide/versions", 1L))
			.andExpect(status().isConflict());
	}

	// ─── POST /dev-guide/regenerate ───────────────────────────────────────────

	@Test
	void regenerateDevGuide_returnsContentAndManualType() throws Exception {
		DevGuideContent content = sampleContent("재생성된 개요입니다.");
		DevGuideRegenerateResponse response =
			new DevGuideRegenerateResponse(content, DevGuideGenerationType.MANUAL, 2);

		when(devGuideService.regenerate(1L, 1L, null)).thenReturn(response);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationType").value("MANUAL"))
			.andExpect(jsonPath("$.remainingRegenerationCount").value(2))
			.andExpect(jsonPath("$.content.overview").value("재생성된 개요입니다."));
	}

	@Test
	void regenerateDevGuide_returnsRecoveryType_whenRetryingAfterFailure() throws Exception {
		DevGuideContent content = sampleContent("복구된 개요입니다.");
		DevGuideRegenerateResponse response =
			new DevGuideRegenerateResponse(content, DevGuideGenerationType.RECOVERY, 3);

		when(devGuideService.regenerate(1L, 1L, null)).thenReturn(response);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationType").value("RECOVERY"))
			.andExpect(jsonPath("$.remainingRegenerationCount").value(3));
	}

	@Test
	void regenerateDevGuide_returnsConflict_whenAlreadyGenerating() throws Exception {
		when(devGuideService.regenerate(1L, 1L, null))
			.thenThrow(new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING));

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isConflict());
	}

	@Test
	void regenerateDevGuide_returnsTooManyRequests_whenLimitExceeded() throws Exception {
		when(devGuideService.regenerate(1L, 1L, null))
			.thenThrow(new ApplicationException(ErrorCode.DEV_GUIDE_REGENERATION_LIMIT_EXCEEDED));

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isTooManyRequests());
	}

	// ─── POST /dev-guide/{devGuideId}/confirm ────────────────────────────────

	@Test
	void confirmDevGuide_returnsOk() throws Exception {
		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/{devGuideId}/confirm", 1L, 2L))
			.andExpect(status().isOk())
			.andExpect(content().string(""));

		verify(devGuideService).confirm(1L, 1L, 2L);
	}

	@Test
	void confirmDevGuide_returnsConflict_whenGuideIsGenerating() throws Exception {
		doThrow(new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING))
			.when(devGuideService).confirm(1L, 1L, 2L);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/{devGuideId}/confirm", 1L, 2L))
			.andExpect(status().isConflict());
	}

	@Test
	void confirmDevGuide_returnsForbidden_whenProjectGroupIsFinished() throws Exception {
		doThrow(new ApplicationException(ErrorCode.DEV_GUIDE_WRITE_NOT_ALLOWED))
			.when(devGuideService).confirm(1L, 1L, 2L);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/{devGuideId}/confirm", 1L, 2L))
			.andExpect(status().isForbidden());
	}

	@Test
	void confirmDevGuide_returnsNotFound_whenDevGuideDoesNotExist() throws Exception {
		doThrow(new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND))
			.when(devGuideService).confirm(1L, 1L, 2L);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/{devGuideId}/confirm", 1L, 2L))
			.andExpect(status().isNotFound());
	}

	// ─── fixtures ────────────────────────────────────────────────────────────

	private DevGuideContent sampleContent(String overview) {
		return new DevGuideContent(
			overview,
			List.of(new DevGuideContent.TechStackItem("Backend", "Spring Boot", "이유입니다.")),
			List.of(new DevGuideContent.MvpPriority(1, "로그인", "핵심 기능입니다.", List.of("회원가입", "로그인", "토큰 발급"))),
			List.of(new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "트레이드오프를 고려합니다.")),
			List.of(new DevGuideContent.Milestone(1, "요구사항 정리",
				new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업")))
		);
	}
}
