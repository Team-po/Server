package team.po.feature.teamspace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomExceptionHandler;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
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
}
