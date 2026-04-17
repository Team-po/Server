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
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.enums.Status;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
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
			.password("password")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(mockUser, "id", 1L);

		// Resolver가 Users 객체를 반환하도록 설정
		when(loginUserArgumentResolver.supportsParameter(any())).thenReturn(true);
		when(loginUserArgumentResolver.resolveArgument(any(), any(), any(), any())).thenReturn(mockUser);

		UserPrincipal principal = new UserPrincipal(1L, "test@email.com");
		Authentication authentication = new UsernamePasswordAuthenticationToken(
			principal, null, List.of()
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// ===== createProjectRequest =====

	@Test
	void createProjectRequest_returnsOk_whenRequestIsValid() throws Exception {
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": null, "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isOk());

		verify(projectRequestService).createProjectRequest(any(Users.class), any());
	}

	@Test
	void createProjectRequest_returnsBadRequest_whenRoleIsNull() throws Exception {
		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": null, "projectTitle": null, "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD));
	}

	@Test
	void createProjectRequest_returnsConflict_whenDuplicateRequest() throws Exception {
		doThrow(new ProjectRequestAlreadyExistsException(
			HttpStatus.CONFLICT,
			ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS,
			"이미 진행 중인 매칭 요청이 있습니다."
		)).when(projectRequestService).createProjectRequest(any(Users.class), any());

		mockMvc.perform(post("/api/match/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role": "BACKEND", "projectTitle": null, "projectDescription": "desc", "projectMvp": "mvp"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.PROJECT_REQUEST_ALREADY_EXISTS))
			.andExpect(jsonPath("$.message").value("이미 진행 중인 매칭 요청이 있습니다."));
	}

	// ===== cancelProjectRequest =====

	@Test
	void cancelProjectRequest_returnsOk() throws Exception {
		mockMvc.perform(patch("/api/match/cancel"))
			.andExpect(status().isOk());

		verify(projectRequestService).cancelProjectRequest(any(Users.class));
	}

	@Test
	void cancelProjectRequest_returnsNotFound_whenNoActiveRequest() throws Exception {
		doThrow(new ProjectRequestNotFoundException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
			"취소할 수 있는 매칭 요청이 없습니다."
		)).when(projectRequestService).cancelProjectRequest(any(Users.class));

		mockMvc.perform(patch("/api/match/cancel"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND))
			.andExpect(jsonPath("$.message").value("취소할 수 있는 매칭 요청이 없습니다."));
	}

	// ===== getProjectRequestStatus =====

	@Test
	void getProjectRequestStatus_returnsOk_whenWaiting() throws Exception {
		when(projectRequestService.getProjectRequestStatus(any(Users.class)))
			.thenReturn(new ProjectRequestStatusResponse(Status.WAITING, null));

		mockMvc.perform(get("/api/match/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.matchId").isEmpty());
	}

	@Test
	void getProjectRequestStatus_returnsOk_whenMatching() throws Exception {
		when(projectRequestService.getProjectRequestStatus(any(Users.class)))
			.thenReturn(new ProjectRequestStatusResponse(Status.MATCHING, 42L));

		mockMvc.perform(get("/api/match/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("MATCHING"))
			.andExpect(jsonPath("$.matchId").value(42));
	}

	@Test
	void getProjectRequestStatus_returnsNotFound_whenNoActiveRequest() throws Exception {
		doThrow(new ProjectRequestNotFoundException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
			"진행 중인 매칭 요청이 없습니다."
		)).when(projectRequestService).getProjectRequestStatus(any(Users.class));

		mockMvc.perform(get("/api/match/status"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND))
			.andExpect(jsonPath("$.message").value("진행 중인 매칭 요청이 없습니다."));
	}
}