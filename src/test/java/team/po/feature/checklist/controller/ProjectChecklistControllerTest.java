package team.po.feature.checklist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
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
import team.po.feature.checklist.domain.ProjectChecklistStatus;
import team.po.feature.checklist.dto.ChecklistAiAdviceResponse;
import team.po.feature.checklist.dto.GenerateChecklistAdviceResponse;
import team.po.feature.checklist.dto.ProjectChecklistResponse;
import team.po.feature.checklist.service.ProjectChecklistService;
import team.po.feature.user.domain.Users;

@WebMvcTest(ProjectChecklistController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class ProjectChecklistControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectChecklistService projectChecklistService;

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
	void getProjectChecklists_returnsChecklistList() throws Exception {
		when(projectChecklistService.getProjectChecklists(eq(10L), any(Users.class)))
			.thenReturn(List.of(mockResponse()));

		mockMvc.perform(get("/api/project-groups/10/checklists"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(100L))
			.andExpect(jsonPath("$[0].aiAdvice.summary").value("핵심 포인트"));
	}

	@Test
	void createProjectChecklist_returnsCreated() throws Exception {
		when(projectChecklistService.createProjectChecklist(eq(10L), any(Users.class), any()))
			.thenReturn(mockResponse());

		mockMvc.perform(post("/api/project-groups/10/checklists")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"예외 처리 정리","description":"설명","dueDate":"2026-05-20","assigneeUserId":1}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("예외 처리 정리"))
			.andExpect(jsonPath("$.assigneeUserId").value(1));
	}

	@Test
	void updateProjectChecklist_returnsOk() throws Exception {
		ProjectChecklistResponse response = new ProjectChecklistResponse(
			100L,
			"예외 처리 정리",
			"새 설명",
			ProjectChecklistStatus.DONE,
			LocalDate.parse("2026-05-21"),
			Instant.parse("2026-05-17T10:00:00Z"),
			1L,
			"tester",
			null,
			"ALL",
			null
		);
		when(projectChecklistService.updateProjectChecklist(eq(10L), eq(100L), any(Users.class), any()))
			.thenReturn(response);

		mockMvc.perform(patch("/api/project-groups/10/checklists/100")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"예외 처리 정리","description":"새 설명","status":"DONE","dueDate":"2026-05-21","assigneeUserId":null}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.assigneeNickname").value("ALL"))
			.andExpect(jsonPath("$.aiAdvice").value(Matchers.nullValue()));
	}

	@Test
	void deleteProjectChecklist_returnsNoContent() throws Exception {
		doNothing().when(projectChecklistService).deleteProjectChecklist(10L, 100L, mockUser);

		mockMvc.perform(delete("/api/project-groups/10/checklists/100"))
			.andExpect(status().isNoContent());
	}

	@Test
	void generateChecklistAdvice_returnsOk() throws Exception {
		when(projectChecklistService.generateChecklistAdvice(eq(10L), eq(100L), any(Users.class)))
			.thenReturn(new GenerateChecklistAdviceResponse(
				100L,
				new ChecklistAiAdviceResponse(
					"핵심 포인트",
					List.of("현재 작업 범위를 먼저 정리한다.", "예외 응답 형식을 통일한다.", "테스트를 먼저 보강한다."),
					List.of("민감한 정보는 노출하지 않는다.", "실패 케이스를 문서화한다."),
					List.of("공통 예외 처리 경로를 사용한다.")
				)
			));

		mockMvc.perform(post("/api/project-groups/10/checklists/100/advice"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checklistId").value(100L))
			.andExpect(jsonPath("$.aiAdvice.recommendedFlow[0]").value("현재 작업 범위를 먼저 정리한다."));
	}

	@Test
	void createProjectChecklist_returnsBadRequest_whenTitleBlank() throws Exception {
		mockMvc.perform(post("/api/project-groups/10/checklists")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title":" ","description":"설명","dueDate":"2026-05-20","assigneeUserId":1}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_FIELD.getCode()));
	}

	@Test
	void generateChecklistAdvice_returnsForbidden_whenAccessDenied() throws Exception {
		doThrow(new ApplicationException(ErrorCode.PROJECT_GROUP_ACCESS_DENIED))
			.when(projectChecklistService).generateChecklistAdvice(eq(10L), eq(100L), any(Users.class));

		mockMvc.perform(post("/api/project-groups/10/checklists/100/advice"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode()));
	}

	private ProjectChecklistResponse mockResponse() {
		return new ProjectChecklistResponse(
			100L,
			"예외 처리 정리",
			"설명",
			ProjectChecklistStatus.TODO,
			LocalDate.parse("2026-05-20"),
			Instant.parse("2026-05-17T10:00:00Z"),
			1L,
			"tester",
			1L,
			"tester",
			new ChecklistAiAdviceResponse(
				"핵심 포인트",
				List.of("현재 작업 범위를 먼저 정리한다.", "예외 응답 형식을 통일한다.", "테스트를 먼저 보강한다."),
				List.of("민감한 정보는 노출하지 않는다.", "실패 케이스를 문서화한다."),
				List.of("공통 예외 처리 경로를 사용한다.")
			)
		);
	}
}
