package team.po.feature.projectgroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.config.WebConfig;
import team.po.exception.CustomExceptionHandler;
import team.po.exception.CustomTeamExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
import team.po.feature.projectgroup.exception.ProjectGroupErrorType;
import team.po.feature.projectgroup.exception.ProjectGroupException;
import team.po.feature.projectgroup.service.ProjectGroupService;

@WebMvcTest(ProjectGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CustomExceptionHandler.class, CustomTeamExceptionHandler.class, WebConfig.class, LoginUserArgumentResolver.class})
class ProjectGroupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectGroupService projectGroupService;

	@MockitoBean
	private UserRepository userRepository;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createProjectGroup_returnsCreated_whenRequestIsValid() throws Exception {
		setAuthenticatedUser(1L, "host@example.com");
		when(projectGroupService.createProjectGroup(anyLong(), any()))
			.thenReturn(new CreateProjectGroupResponse(1L, "Teampo Alpha", "주제 A", "ACTIVE", 1L, 4));

		mockMvc.perform(post("/api/project-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "hostUserId": 1,
						  "matchId": 1001,
						  "members": [
						{"userId":1,"role":"BACKEND"},
						{"userId":2,"role":"FRONTEND"},
						{"userId":3,"role":"DESIGN"},
						{"userId":4,"role":"BACKEND"}
					  ],
					  "projectName": "Teampo Alpha",
					  "projectTitle": "주제 A",
					  "projectDescription": "설명",
					  "projectMvp": "MVP",
					  "status": "ACTIVE"
					}
					"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.groupId").value(1))
				.andExpect(jsonPath("$.projectName").value("Teampo Alpha"))
				.andExpect(jsonPath("$.hostUserId").value(1))
				.andExpect(jsonPath("$.memberCount").value(4));

		verify(projectGroupService).createProjectGroup(anyLong(), any());
	}

	@Test
	void createProjectGroup_returnsBadRequest_whenValidationFails() throws Exception {
		setAuthenticatedUser(1L, "host@example.com");

		mockMvc.perform(post("/api/project-groups")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "hostUserId": 1,
							  "matchId": 1002,
						  "members": [
						{"userId":1,"role":"BACKEND"},
						{"userId":2,"role":"FRONTEND"},
						{"userId":3,"role":"DESIGN"}
					  ],
					  "projectName": "",
					  "projectTitle": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD));
	}

	@Test
	void createProjectGroup_returnsBadRequest_whenHostIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "host@example.com");
		doThrow(new ProjectGroupException(
			ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
			"방장 식별자는 팀 구성원 목록에 포함되어야 합니다."
		)).when(projectGroupService).createProjectGroup(anyLong(), any());

		mockMvc.perform(post("/api/project-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "hostUserId": 99,
						  "matchId": 1003,
						  "members": [
						{"userId":1,"role":"BACKEND"},
						{"userId":2,"role":"FRONTEND"},
						{"userId":3,"role":"DESIGN"},
						{"userId":4,"role":"BACKEND"}
					  ],
					  "projectName": "Teampo Alpha",
					  "projectTitle": "주제 A"
					}
					"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST));
	}

	@Test
	void createProjectGroup_returnsUnauthorized_whenAuthenticationMissing() throws Exception {
		mockMvc.perform(post("/api/project-groups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "hostUserId": 1,
						  "matchId": 1004,
						  "members": [
						{"userId":1,"role":"BACKEND"},
						{"userId":2,"role":"FRONTEND"},
						{"userId":3,"role":"DESIGN"},
						{"userId":4,"role":"BACKEND"}
					  ],
					  "projectName": "Teampo Alpha",
					  "projectTitle": "주제 A"
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.NO_AUTHENTICATED_USER));
	}

	private void setAuthenticatedUser(Long userId, String email) {
		Users user = Users.builder()
			.email(email)
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

		UserPrincipal principal = new UserPrincipal(userId, email);
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
