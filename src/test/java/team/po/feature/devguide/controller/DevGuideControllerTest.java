package team.po.feature.devguide.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.exception.CustomExceptionHandler;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.service.DevGuideService;

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

		when(devGuideService.getDevGuide(1L)).thenReturn(content);

		mockMvc.perform(get("/api/team-space/{projectGroupId}/dev-guide", 1L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overview").value("프로젝트 개요입니다."))
			.andExpect(jsonPath("$.techStack[0].category").value("Backend"))
			.andExpect(jsonPath("$.mvpPriorities[0].priority").value(1))
			.andExpect(jsonPath("$.decisionPoints[0].topic").value("인증 방식"))
			.andExpect(jsonPath("$.milestones[0].week").value(1));
	}
}