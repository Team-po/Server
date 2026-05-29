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
import org.springframework.http.MediaType;
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
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Status;
import team.po.feature.match.service.ProjectRequestService;
import team.po.feature.user.domain.Users;

@WebMvcTest(ProjectRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
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

	// ===== createProjectRequest =====

	@Test
	void createProjectRequest_returnsOk_whenRequestIsMember() throws Exception {
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": null, "projectDescription": null, "projectMvp": null}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void createProjectRequest_returnsOk_whenRequestIsHost() throws Exception {
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void createProjectRequest_returnsBadRequest_whenHostInputIncomplete() throws Exception {
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": null, "projectMvp": "mvp"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_FIELD.getCode()));
	}

	@Test
	void createProjectRequest_returnsConflict_whenDuplicateRequest() throws Exception {
		doThrow(new ApplicationException(ErrorCode.PROJECT_REQUEST_ALREADY_EXISTS))
			.when(projectRequestService).createProjectRequest(any(Users.class), any());

		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": "title", "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_REQUEST_ALREADY_EXISTS.getCode()))
			.andExpect(jsonPath("$.message").value("이미 진행 중인 매칭 요청이 있습니다."));
	}

	// ===== getProjectRequestStatus =====

	@Test
	void getProjectRequestStatus_returnsOk() throws Exception {
		when(projectRequestService.getProjectRequestStatus(any(Users.class)))
			.thenReturn(new ProjectRequestStatusResponse(Status.WAITING, null));

		mockMvc.perform(get("/api/match/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WAITING"));
	}

	@Test
	void getProjectRequestStatus_returnsNotFound_whenNoActiveRequest() throws Exception {
		doThrow(new ApplicationException(ErrorCode.PROJECT_REQUEST_NOT_FOUND))
			.when(projectRequestService).getProjectRequestStatus(any(Users.class));

		mockMvc.perform(get("/api/match/status"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_REQUEST_NOT_FOUND.getCode()))
			.andExpect(jsonPath("$.message").value("진행 중인 매칭 요청이 없습니다."));
	}
}