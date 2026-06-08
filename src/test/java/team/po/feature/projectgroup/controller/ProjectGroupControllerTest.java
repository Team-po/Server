package team.po.feature.projectgroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import team.po.exception.ApplicationException;
import team.po.exception.CustomExceptionHandler;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.service.ProjectGroupService;
import team.po.feature.user.domain.Users;

@WebMvcTest(ProjectGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class ProjectGroupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectGroupService projectGroupService;

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
	void finishProjectGroup_returnsOk_whenMemberAgrees() throws Exception {
		mockMvc.perform(patch("/api/project-groups/{projectGroupId}/finish", 10L))
			.andExpect(status().isOk());

		verify(projectGroupService).finishProjectGroup(10L, 1L);
	}

	@Test
	void finishProjectGroup_returnsForbidden_whenRequesterIsNotMember() throws Exception {
		doThrow(new ApplicationException(
			ErrorCode.PROJECT_GROUP_ACCESS_DENIED,
			"팀원만 팀 스페이스 종료에 동의할 수 있습니다."
		)).when(projectGroupService).finishProjectGroup(anyLong(), anyLong());

		mockMvc.perform(patch("/api/project-groups/{projectGroupId}/finish", 10L))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode()))
			.andExpect(jsonPath("$.message").value("팀원만 팀 스페이스 종료에 동의할 수 있습니다."));
	}
}
