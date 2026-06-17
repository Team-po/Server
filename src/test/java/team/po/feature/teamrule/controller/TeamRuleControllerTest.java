package team.po.feature.teamrule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.exception.ApplicationException;
import team.po.exception.CustomExceptionHandler;
import team.po.exception.ErrorCode;
import team.po.feature.teamrule.dto.TeamRuleResponse;
import team.po.feature.teamrule.service.TeamRuleService;
import team.po.feature.user.domain.Users;

@WebMvcTest(TeamRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class TeamRuleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TeamRuleService teamRuleService;

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
	void getTeamRule_returnsTeamRule() throws Exception {
		when(teamRuleService.getTeamRule(eq(10L), any(Users.class))).thenReturn(mockResponse());

		mockMvc.perform(get("/api/team-space/{projectGroupId}/team-rule", 10L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(100L))
			.andExpect(jsonPath("$.projectGroupId").value(10L))
			.andExpect(jsonPath("$.content").value("# 팀 룰"))
			.andExpect(jsonPath("$.version").value(3L))
			.andExpect(jsonPath("$.updatedByNickname").value("tester"));
	}

	@Test
	void updateTeamRule_returnsUpdatedTeamRule() throws Exception {
		when(teamRuleService.updateTeamRule(eq(10L), any(Users.class), any())).thenReturn(mockResponse());

		mockMvc.perform(put("/api/team-space/{projectGroupId}/team-rule", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"# 팀 룰","version":3}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(3L))
			.andExpect(jsonPath("$.content").value("# 팀 룰"));
	}

	@Test
	void updateTeamRule_returnsBadRequest_whenContentBlank() throws Exception {
		mockMvc.perform(put("/api/team-space/{projectGroupId}/team-rule", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"   ","version":3}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_FIELD.getCode()));
	}

	@Test
	void updateTeamRule_returnsBadRequest_whenVersionMissing() throws Exception {
		mockMvc.perform(put("/api/team-space/{projectGroupId}/team-rule", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"# 팀 룰"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_FIELD.getCode()));
	}

	@Test
	void updateTeamRule_returnsConflict_whenVersionConflicts() throws Exception {
		doThrow(new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT))
			.when(teamRuleService).updateTeamRule(eq(10L), any(Users.class), any());

		mockMvc.perform(put("/api/team-space/{projectGroupId}/team-rule", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"# 팀 룰","version":2}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT.getCode()));
	}

	@Test
	void updateTeamRule_returnsForbidden_whenProjectGroupFinished() throws Exception {
		doThrow(new ApplicationException(ErrorCode.PROJECT_TEAM_RULE_WRITE_NOT_ALLOWED))
			.when(teamRuleService).updateTeamRule(eq(10L), any(Users.class), any());

		mockMvc.perform(put("/api/team-space/{projectGroupId}/team-rule", 10L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"# 팀 룰","version":3}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_TEAM_RULE_WRITE_NOT_ALLOWED.getCode()));
	}

	private TeamRuleResponse mockResponse() {
		return new TeamRuleResponse(
			100L,
			10L,
			"# 팀 룰",
			3L,
			Instant.parse("2026-06-10T10:00:00Z"),
			"tester"
		);
	}
}
