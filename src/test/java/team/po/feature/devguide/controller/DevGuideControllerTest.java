package team.po.feature.devguide.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.dto.DevGuideRegenerateResponse;

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
import team.po.exception.CustomExceptionHandler;
import team.po.feature.devguide.dto.DevGuideContent;
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

	@Test
	void getDevGuide_returnsDevGuideContent() throws Exception {
		DevGuideContent content = new DevGuideContent(
			"프로젝트 개요입니다.",
			List.of(new DevGuideContent.TechStackItem("Backend", "Spring Boot", "이유입니다.")),
			List.of(new DevGuideContent.MvpPriority(1, "로그인", "핵심 기능입니다.", List.of("회원가입", "로그인", "토큰 발급"))),
			List.of(new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "트레이드오프를 고려합니다.")),
			List.of(new DevGuideContent.Milestone(1, "요구사항 정리",
				new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업")))
		);

		when(devGuideService.getDevGuide(1L, 1L)).thenReturn(content);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overview").value("프로젝트 개요입니다."))
			.andExpect(jsonPath("$.techStack[0].category").value("Backend"))
			.andExpect(jsonPath("$.mvpPriorities[0].priority").value(1))
			.andExpect(jsonPath("$.decisionPoints[0].topic").value("인증 방식"))
			.andExpect(jsonPath("$.milestones[0].week").value(1));
	}

	@Test
	void regenerateDevGuide_returnsContentAndGenerationType() throws Exception {
		DevGuideContent content = new DevGuideContent(
			"재생성된 프로젝트 개요입니다.",
			List.of(new DevGuideContent.TechStackItem("Backend", "Spring Boot", "이유입니다.")),
			List.of(new DevGuideContent.MvpPriority(1, "로그인", "핵심 기능입니다.", List.of("회원가입", "로그인", "토큰 발급"))),
			List.of(new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "트레이드오프를 고려합니다.")),
			List.of(new DevGuideContent.Milestone(1, "요구사항 정리",
				new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업")))
		);
		DevGuideRegenerateResponse response =
			new DevGuideRegenerateResponse(content, DevGuideGenerationType.MANUAL, null);

		when(devGuideService.regenerate(1L, 1L)).thenReturn(response);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.overview").value("재생성된 프로젝트 개요입니다."))
			.andExpect(jsonPath("$.content.techStack[0].category").value("Backend"))
			.andExpect(jsonPath("$.generationType").value("MANUAL"))
			.andExpect(jsonPath("$.remainingRegenerationCount").doesNotExist());
	}

	@Test
	void regenerateDevGuide_returnsRecoveryType_whenNoConfirmedGuideExisted() throws Exception {
		DevGuideContent content = new DevGuideContent(
			"복구된 프로젝트 개요입니다.",
			List.of(new DevGuideContent.TechStackItem("Backend", "Spring Boot", "이유입니다.")),
			List.of(new DevGuideContent.MvpPriority(1, "로그인", "핵심 기능입니다.", List.of("회원가입", "로그인", "토큰 발급"))),
			List.of(new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "트레이드오프를 고려합니다.")),
			List.of(new DevGuideContent.Milestone(1, "요구사항 정리",
				new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업")))
		);
		DevGuideRegenerateResponse response =
			new DevGuideRegenerateResponse(content, DevGuideGenerationType.RECOVERY, null);

		when(devGuideService.regenerate(1L, 1L)).thenReturn(response);

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generationType").value("RECOVERY"));
	}

	@Test
	void regenerateDevGuide_returnsNotFound_whenProjectGroupDoesNotExist() throws Exception {
		when(devGuideService.regenerate(1L, 1L))
			.thenThrow(new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		mockMvc.perform(post("/api/team-space/{projectGroupId}/dev-guide/regenerate", 1L))
			.andExpect(status().isNotFound());
	}
}