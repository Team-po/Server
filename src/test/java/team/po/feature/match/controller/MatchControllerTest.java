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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserArgumentResolver;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomMatchExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.exception.MatchAccessDeniedException;
import team.po.feature.match.exception.MatchDataIntegrityException;
import team.po.feature.match.service.MatchService;
import team.po.feature.user.domain.Users;

@WebMvcTest(MatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomMatchExceptionHandler.class)
class MatchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MatchService matchService;

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

	// ===== getMatchMembers =====

	@Test
	void getMatchMembers_returnsOk() throws Exception {
		MatchMemberResponse response = new MatchMemberResponse(
			42L,
			List.of(new MatchMemberResponse.MemberDto(
				1L, "tester", Role.BACKEND, 1, 50, null, true, true
			))
		);
		when(matchService.getMatchMembers(eq(42L), any(Users.class))).thenReturn(response);

		mockMvc.perform(get("/api/match/42/members"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(42))
			.andExpect(jsonPath("$.members[0].nickname").value("tester"));
	}

	@Test
	void getMatchMembers_returnsNotFound_whenSessionNotExists() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.MATCH_NOT_FOUND,
			"존재하지 않는 매칭 세션입니다."
		)).when(matchService).getMatchMembers(eq(42L), any(Users.class));

		mockMvc.perform(get("/api/match/42/members"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_NOT_FOUND))
			.andExpect(jsonPath("$.message").value("존재하지 않는 매칭 세션입니다."));
	}

	@Test
	void getMatchMembers_returnsForbidden_whenNotMember() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.FORBIDDEN,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"해당 매칭 세션에 접근 권한이 없습니다."
		)).when(matchService).getMatchMembers(eq(42L), any(Users.class));

		mockMvc.perform(get("/api/match/42/members"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED))
			.andExpect(jsonPath("$.message").value("해당 매칭 세션에 접근 권한이 없습니다."));
	}

	// ===== getMatchProject =====

	@Test
	void getMatchProject_returnsOk() throws Exception {
		MatchProjectResponse response = new MatchProjectResponse(42L, "팀포", "설명", "MVP");
		when(matchService.getMatchProject(eq(42L), any(Users.class))).thenReturn(response);

		mockMvc.perform(get("/api/match/42/project"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(42))
			.andExpect(jsonPath("$.projectTitle").value("팀포"));
	}

	@Test
	void getMatchProject_returnsNotFound_whenSessionNotExists() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.MATCH_NOT_FOUND,
			"존재하지 않는 매칭 세션입니다."
		)).when(matchService).getMatchProject(eq(42L), any(Users.class));

		mockMvc.perform(get("/api/match/42/project"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_NOT_FOUND))
			.andExpect(jsonPath("$.message").value("존재하지 않는 매칭 세션입니다."));
	}

	@Test
	void getMatchProject_returnsForbidden_whenNotMember() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.FORBIDDEN,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"해당 매칭 세션에 접근 권한이 없습니다."
		)).when(matchService).getMatchProject(eq(42L), any(Users.class));

		mockMvc.perform(get("/api/match/42/project"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED))
			.andExpect(jsonPath("$.message").value("해당 매칭 세션에 접근 권한이 없습니다."));
	}

	@Test
	void getMatchProject_returnsInternalServerError_whenDataIntegrity() throws Exception {
		doThrow(new MatchDataIntegrityException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			ErrorCodeConstants.MATCH_DATA_ERROR,
			"해당 매칭 세션에 호스트 정보가 없거나 중복되었습니다."
		)).when(matchService).getMatchProject(eq(42L), any(Users.class));

		mockMvc.perform(get("/api/match/42/project"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_DATA_ERROR));
	}

	// ===== accept =====

	@Test
	void accept_returnsOk() throws Exception {
		doNothing().when(matchService).accept(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/accept"))
			.andExpect(status().isOk());
	}

	@Test
	void accept_returnsNotFound_whenSessionNotExists() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.MATCH_NOT_FOUND,
			"존재하지 않는 매칭 세션입니다."
		)).when(matchService).accept(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/accept"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_NOT_FOUND));
	}

	@Test
	void accept_returnsForbidden_whenHost() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.FORBIDDEN,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"호스트는 수락 또는 거절할 수 없습니다."
		)).when(matchService).accept(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/accept"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED));
	}

	@Test
	void accept_returnsBadRequest_whenAlreadyRejected() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.BAD_REQUEST,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"이미 거절한 매칭 세션입니다."
		)).when(matchService).accept(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/accept"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED));
	}

	// ===== reject =====

	@Test
	void reject_returnsOk() throws Exception {
		doNothing().when(matchService).reject(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/reject"))
			.andExpect(status().isOk());
	}

	@Test
	void reject_returnsNotFound_whenSessionNotExists() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.MATCH_NOT_FOUND,
			"존재하지 않는 매칭 세션입니다."
		)).when(matchService).reject(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/reject"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_NOT_FOUND));
	}

	@Test
	void reject_returnsForbidden_whenHost() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.FORBIDDEN,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"호스트는 수락 또는 거절할 수 없습니다."
		)).when(matchService).reject(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/reject"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED));
	}

	@Test
	void reject_returnsBadRequest_whenAlreadyAccepted() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.BAD_REQUEST,
			ErrorCodeConstants.MATCH_ACCESS_DENIED,
			"이미 수락한 매칭 세션입니다."
		)).when(matchService).reject(eq(42L), any(Users.class));

		mockMvc.perform(post("/api/match/42/reject"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.MATCH_ACCESS_DENIED));
	}

	// ===== cancel =====

	@Test
	void cancel_returnsOk() throws Exception {
		doNothing().when(matchService).cancel(any(Users.class));

		mockMvc.perform(post("/api/match/cancel"))
			.andExpect(status().isOk());
	}

	@Test
	void cancel_returnsNotFound_whenNoActiveRequest() throws Exception {
		doThrow(new MatchAccessDeniedException(
			HttpStatus.NOT_FOUND,
			ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
			"취소할 수 있는 매칭 요청이 없습니다."
		)).when(matchService).cancel(any(Users.class));

		mockMvc.perform(post("/api/match/cancel"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND));
	}
}