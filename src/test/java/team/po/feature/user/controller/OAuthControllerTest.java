package team.po.feature.user.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.jwt.UserPrincipal;
import team.po.exception.ApplicationException;
import team.po.exception.CustomExceptionHandler;
import team.po.exception.ErrorCode;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;
import team.po.feature.user.service.GithubOAuthService;

@WebMvcTest(OAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class OAuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GithubOAuthService githubOAuthService;

	@MockitoBean
	private UserRepository userRepository;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void startGithubAccountLink_returnsAuthorizationUrl_whenAuthenticatedUserExists() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		when(githubOAuthService.createGithubLinkCode(authenticatedUser)).thenReturn("link-code");

		mockMvc.perform(post("/api/oauth/github/link-requests"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authorizationUrl").value("/oauth2/authorization/github?linkCode=link-code"));
	}

	@Test
	void startGithubAccountLink_returnsConflict_whenGithubAccountIsAlreadyLinked() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		when(githubOAuthService.createGithubLinkCode(authenticatedUser))
			.thenThrow(new ApplicationException(ErrorCode.GITHUB_ACCOUNT_ALREADY_LINKED));

		mockMvc.perform(post("/api/oauth/github/link-requests"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.GITHUB_ACCOUNT_ALREADY_LINKED.getCode()))
			.andExpect(jsonPath("$.message").value("이미 GitHub 계정이 연동되어 있습니다."));
	}

	@Test
	void unlinkGithubAccount_returnsNoContent_whenGithubAccountIsLinked() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(delete("/api/oauth/github/account"))
			.andExpect(status().isNoContent());

		verify(githubOAuthService).unlinkGithubAccount(authenticatedUser);
	}

	@Test
	void unlinkGithubAccount_returnsNotFound_whenGithubAccountIsNotLinked() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new ApplicationException(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED))
			.when(githubOAuthService).unlinkGithubAccount(authenticatedUser);

		mockMvc.perform(delete("/api/oauth/github/account"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED.getCode()))
			.andExpect(jsonPath("$.message").value("연동된 GitHub 계정이 없습니다."));
	}

	@Test
	void unlinkGithubAccount_returnsConflict_whenUserIsGithubLoginAccount() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new ApplicationException(ErrorCode.GITHUB_LOGIN_ACCOUNT_UNLINK_NOT_ALLOWED))
			.when(githubOAuthService).unlinkGithubAccount(authenticatedUser);

		mockMvc.perform(delete("/api/oauth/github/account"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.GITHUB_LOGIN_ACCOUNT_UNLINK_NOT_ALLOWED.getCode()))
			.andExpect(jsonPath("$.message").value("GitHub 로그인 계정은 GitHub 연동을 해제할 수 없습니다."));
	}

	private Users setAuthenticatedUser(Long id, String email) {
		Users user = Users.builder()
			.email(email)
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(user));
		UserPrincipal principal = new UserPrincipal(id, email);
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return user;
	}
}
