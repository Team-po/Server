package team.po.feature.match.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomProjectRequestExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.service.ProjectRequestService;
import team.po.feature.user.domain.Users;

@WebMvcTest(ProjectRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomProjectRequestExceptionHandler.class)
class ProjectRequestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectRequestService projectRequestService;

	@MockitoBean
	private LoginUserArgumentResolver loginUserArgumentResolver;

	private Users mockUser;

	@BeforeEach
	void setUp() throws Exception {
		mockUser = Users.builder()
			.email("test@email.com")
			.nickname("tester")
			.build();
		ReflectionTestUtils.setField(mockUser, "id", 1L);

		when(loginUserArgumentResolver.supportsParameter(any())).thenReturn(true);
		when(loginUserArgumentResolver.resolveArgument(any(), any(), any(), any())).thenReturn(mockUser);

		UserPrincipal principal = new UserPrincipal(1L, "test@email.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createProjectRequest_returnsOk_whenRequestIsMember() throws Exception {
		// Member 신청: 제목/설명/MVP 모두 비어있어야 함
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": null, "projectDescription": null, "projectMvp": null}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void createProjectRequest_returnsOk_whenRequestIsHost() throws Exception {
		// Host 신청: 제목/설명/MVP 모두 존재해야 함
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void createProjectRequest_returnsBadRequest_whenHostInputIncomplete() throws Exception {
		// Host 신청 시도 중 하나만 누락된 경우 (Validation 작동 확인)
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": null, "projectMvp": "mvp"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD));
	}

	@Test
	void createProjectRequest_returnsConflict_whenDuplicateRequest() throws Exception {
		doThrow(new ProjectRequestAlreadyExistsException(
			HttpStatus.CONFLICT,
			ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
			"중복"
		)).when(projectRequestService).createProjectRequest(any(), any());

		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isConflict());
	}

	// getProjectRequestStatus 테스트는 기존과 동일하게 유지
}