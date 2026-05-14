package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.redis.RedisService;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.user.domain.GithubAccount;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.GithubAuthorizationCode;
import team.po.feature.user.dto.OAuthAuthorizationCodeRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.repository.GithubAccountRepository;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GithubOAuthServiceTest {
	private static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(3);
	private static final String AUTHORIZATION_CODE_KEY = "github-oauth-code:authorization-code";
	private static final Instant ACCESS_TOKEN_EXPIRES_AT = Instant.parse("2026-05-06T12:00:00Z");

	@Mock
	private GithubAccountRepository githubAccountRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RedisService redisService;

	@Mock
	private RestClient restClient;

	@Mock
	private GithubTokenEncryptor githubTokenEncryptor;

	@InjectMocks
	private GithubOAuthService githubOAuthService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(githubOAuthService, "authorizationCodeTtl", AUTHORIZATION_CODE_TTL);
	}

	@Test
	void createGithubAuthorizationCode_returnsLoginCodeWhenGithubAccountIsConnectedToActiveUser() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		GithubAccount githubAccount = githubAccount(user, 123L, "octocat");
		OAuth2User oAuth2User = githubOAuth2User(123L, "octocat", " Test@Email.com ");
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.of(githubAccount));

		GithubAuthorizationCode authorizationCode =
			githubOAuthService.createGithubAuthorizationCode(oAuth2User, "github-access-token");

		assertThat(authorizationCode.authorizationCode()).isNotBlank();
		assertThat(authorizationCode.onboardingRequired()).isFalse();
		verify(redisService).setValue(
			eq("github-oauth-code:" + authorizationCode.authorizationCode()),
			eq("LOGIN.1"),
			eq(AUTHORIZATION_CODE_TTL)
		);
		verifyNoInteractions(restClient);
	}

	@Test
	void createGithubLinkCode_storesCurrentUserIdInRedis() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		String linkCode = githubOAuthService.createGithubLinkCode(user);

		assertThat(linkCode).isNotBlank();
		verify(redisService).setValue("github-oauth-link-code:" + linkCode, "1", AUTHORIZATION_CODE_TTL);
	}

	@Test
	void createGithubLinkCode_throwsWhenCurrentUserAlreadyHasGithubAccount() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L))
			.thenReturn(Optional.of(githubAccount(user, 123L, "octocat")));

		assertThatThrownBy(() -> githubOAuthService.createGithubLinkCode(user))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_ACCOUNT_ALREADY_LINKED);
		verify(redisService, never()).setValue(anyString(), any(), any());
	}

	@Test
	void bindGithubLinkState_storesStateMappingWhenLinkCodeExists() {
		when(redisService.getAndDeleteStringValue("github-oauth-link-code:link-code")).thenReturn("1");

		githubOAuthService.bindGithubLinkState("oauth-state", "link-code");

		verify(redisService).setValue("github-oauth-link-state:oauth-state", "1", AUTHORIZATION_CODE_TTL);
	}

	@Test
	void bindGithubLinkState_storesInvalidMarkerWhenLinkCodeDoesNotExist() {
		when(redisService.getAndDeleteStringValue("github-oauth-link-code:expired-link-code")).thenReturn(null);

		githubOAuthService.bindGithubLinkState("oauth-state", "expired-link-code");

		verify(redisService).setValue("github-oauth-link-state:oauth-state", "INVALID", AUTHORIZATION_CODE_TTL);
	}

	@Test
	void linkGithubAccountIfRequested_returnsFalseWhenStateIsNotForLink() {
		boolean linked = githubOAuthService.linkGithubAccountIfRequested(
			"oauth-state",
			githubOAuth2User(123L, "octocat", "test@email.com"),
			"github-access-token",
			"Bearer",
			Set.of("user:email")
		);

		assertThat(linked).isFalse();
		verify(redisService).getAndDeleteStringValue("github-oauth-link-state:oauth-state");
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void linkGithubAccountIfRequested_savesGithubAccountForCurrentUser() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		OAuth2User oAuth2User = githubOAuth2User(123L, "octocat", "test@email.com");
		when(redisService.getAndDeleteStringValue("github-oauth-link-state:oauth-state")).thenReturn("1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());
		when(githubTokenEncryptor.encrypt("github-access-token")).thenReturn("encrypted-token");

		boolean linked = githubOAuthService.linkGithubAccountIfRequested(
			"oauth-state",
			oAuth2User,
			"github-access-token",
			"Bearer",
			Set.of("repo", "user:email")
		);

		assertThat(linked).isTrue();
		ArgumentCaptor<GithubAccount> githubAccountCaptor = ArgumentCaptor.forClass(GithubAccount.class);
		verify(githubAccountRepository).save(githubAccountCaptor.capture());
		GithubAccount savedGithubAccount = githubAccountCaptor.getValue();
		assertThat(savedGithubAccount.getUser()).isSameAs(user);
		assertThat(savedGithubAccount.getGithubUserId()).isEqualTo(123L);
		assertThat(savedGithubAccount.getGithubUsername()).isEqualTo("octocat");
		assertThat(savedGithubAccount.getAccessTokenCiphertext()).isEqualTo("encrypted-token");
		assertThat(savedGithubAccount.getTokenType()).isEqualTo("Bearer");
		assertThat(savedGithubAccount.getGithubScopes()).isEqualTo("repo,user:email");
	}

	@Test
	void linkGithubAccountIfRequested_throwsWhenCurrentUserAlreadyHasGithubAccount() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		when(redisService.getAndDeleteStringValue("github-oauth-link-state:oauth-state")).thenReturn("1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L))
			.thenReturn(Optional.of(githubAccount(user, 123L, "octocat")));

		assertThatThrownBy(() -> githubOAuthService.linkGithubAccountIfRequested(
			"oauth-state",
			githubOAuth2User(456L, "other", "other@email.com"),
			"github-access-token",
			"Bearer",
			Set.of("user:email")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_ACCOUNT_ALREADY_LINKED);
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void linkGithubAccountIfRequested_throwsWhenGithubAccountIsLinkedToAnotherUser() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		Users anotherUser = githubUser(2L, "other@email.com", "other", 4);
		when(redisService.getAndDeleteStringValue("github-oauth-link-state:oauth-state")).thenReturn("1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L))
			.thenReturn(Optional.of(githubAccount(anotherUser, 123L, "octocat")));

		assertThatThrownBy(() -> githubOAuthService.linkGithubAccountIfRequested(
			"oauth-state",
			githubOAuth2User(123L, "octocat", "octocat@email.com"),
			"github-access-token",
			"Bearer",
			Set.of("user:email")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_ACCOUNT_LINKED_TO_ANOTHER_USER);
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void linkGithubAccountIfRequested_wrapsDataIntegrityViolationAsGithubAccountConflict() {
		Users user = githubUser(1L, "test@email.com", "tester", 3);
		when(redisService.getAndDeleteStringValue("github-oauth-link-state:oauth-state")).thenReturn("1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());
		when(githubTokenEncryptor.encrypt("github-access-token")).thenReturn("encrypted-token");
		when(githubAccountRepository.save(any(GithubAccount.class)))
			.thenThrow(new DataIntegrityViolationException("Duplicate entry"));

		assertThatThrownBy(() -> githubOAuthService.linkGithubAccountIfRequested(
			"oauth-state",
			githubOAuth2User(123L, "octocat", "octocat@email.com"),
			"github-access-token",
			"Bearer",
			Set.of("user:email")
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_ACCOUNT_LINKED_TO_ANOTHER_USER);
	}

	@Test
	void createGithubAuthorizationCode_returnsLoginCodeWithoutEmailLookupWhenGithubAccountExists() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		GithubAccount githubAccount = githubAccount(user, 123L, "octocat");
		OAuth2User oAuth2User = githubOAuth2UserWithoutEmail(123L, "octocat");
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.of(githubAccount));

		GithubAuthorizationCode authorizationCode =
			githubOAuthService.createGithubAuthorizationCode(oAuth2User, "github-access-token");

		assertThat(authorizationCode.authorizationCode()).isNotBlank();
		assertThat(authorizationCode.onboardingRequired()).isFalse();
		verify(redisService).setValue(
			eq("github-oauth-code:" + authorizationCode.authorizationCode()),
			eq("LOGIN.1"),
			eq(AUTHORIZATION_CODE_TTL)
		);
		verifyNoInteractions(restClient);
	}

	@Test
	void createGithubAuthorizationCode_returnsSignUpCodeWhenGithubAccountDoesNotExist() {
		OAuth2User oAuth2User = githubOAuth2User(123L, "octocat", " Test@Email.com ");
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());

		GithubAuthorizationCode authorizationCode =
			githubOAuthService.createGithubAuthorizationCode(oAuth2User, "github-access-token");

		assertThat(authorizationCode.authorizationCode()).isNotBlank();
		assertThat(authorizationCode.onboardingRequired()).isTrue();

		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(redisService).setValue(
			eq("github-oauth-code:" + authorizationCode.authorizationCode()),
			payloadCaptor.capture(),
			eq(AUTHORIZATION_CODE_TTL)
		);
		assertThat(payloadCaptor.getValue().toString())
			.isEqualTo(signUpPayload(123L, "octocat", "test@email.com"));
	}

	@Test
	void exchangeGithubAuthorizationCode_returnsTokenWithoutLevelWhenPayloadIsLogin() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY)).thenReturn("LOGIN.1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.expiresAt()).isEqualTo(ACCESS_TOKEN_EXPIRES_AT);
		verify(githubAccountRepository, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void exchangeGithubAuthorizationCode_throwsServerErrorWhenLoginUserDoesNotExist() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY)).thenReturn("LOGIN.1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> githubOAuthService.exchangeGithubAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_LOGIN_USER_NOT_FOUND);
	}

	@Test
	void exchangeGithubAuthorizationCode_throwsWhenAuthorizationCodeIsExpiredOrInvalid() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY)).thenReturn(null);

		assertThatThrownBy(() -> githubOAuthService.exchangeGithubAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
	}

	@Test
	void exchangeGithubAuthorizationCode_throwsWhenSignUpPayloadHasNoLevel() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));

		assertThatThrownBy(() -> githubOAuthService.exchangeGithubAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("레벨 선택은 필수입니다.")
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_INPUT_FIELD);
		verifyNoInteractions(userRepository);
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void exchangeGithubAuthorizationCode_createsUserAndGithubAccountWhenPayloadIsSignUp() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 4);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
			Users savedUser = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedUser, "id", 1L);
			return savedUser;
		});
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");

		ArgumentCaptor<Users> userCaptor = ArgumentCaptor.forClass(Users.class);
		verify(userRepository).save(userCaptor.capture());
		Users savedUser = userCaptor.getValue();
		assertThat(savedUser.getEmail()).isEqualTo("test@email.com");
		assertThat(savedUser.getNickname()).isEqualTo("octocat");
		assertThat(savedUser.getLevel()).isEqualTo(4);
		assertThat(savedUser.getTemperature()).isEqualTo(50);
		assertThat(savedUser.getPassword()).isNull();
		assertThat(savedUser.getProfileImage()).isNull();
		assertThat(savedUser.getDescription()).isNull();
		assertThat(savedUser.isGithubLogin()).isTrue();

		ArgumentCaptor<GithubAccount> githubAccountCaptor = ArgumentCaptor.forClass(GithubAccount.class);
		verify(githubAccountRepository).save(githubAccountCaptor.capture());
		GithubAccount savedGithubAccount = githubAccountCaptor.getValue();
		assertThat(savedGithubAccount.getUser()).isSameAs(savedUser);
		assertThat(savedGithubAccount.getGithubUserId()).isEqualTo(123L);
		assertThat(savedGithubAccount.getGithubUsername()).isEqualTo("octocat");
	}

	@Test
	void exchangeGithubAuthorizationCode_throwsEmailAlreadyExistsWhenUserSaveConflicts() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 4);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		when(userRepository.save(any(Users.class)))
			.thenThrow(new DataIntegrityViolationException("Duplicate entry for uq_users_email"));

		assertThatThrownBy(() -> githubOAuthService.exchangeGithubAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("이미 가입된 이메일입니다.")
			.extracting("errorCode")
			.isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
		verify(githubAccountRepository, never()).save(any());
		verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString());
	}

	@Test
	void exchangeGithubAuthorizationCode_createsNewGithubAccountWhenPreviousGithubAccountWasSoftDeleted() {
		Users deletedUser = githubUser(1L, "deleted-test@email.com", "old-octocat", 2);
		deletedUser.softDelete(Instant.parse("2026-05-06T09:00:00Z"), "deleted-test@email.com");
		GithubAccount githubAccount = githubAccount(deletedUser, 123L, "old-octocat");
		githubAccount.softDelete(Instant.parse("2026-05-06T09:00:00Z"));
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 5);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.empty());
		when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
			Users savedUser = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedUser, "id", 2L);
			return savedUser;
		});
		when(jwtTokenProvider.generateToken(2L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(githubAccount.getUser()).isSameAs(deletedUser);
		assertThat(githubAccount.getDeletedAt()).isNotNull();

		ArgumentCaptor<GithubAccount> githubAccountCaptor = ArgumentCaptor.forClass(GithubAccount.class);
		verify(githubAccountRepository).save(githubAccountCaptor.capture());
		GithubAccount savedGithubAccount = githubAccountCaptor.getValue();
		assertThat(savedGithubAccount.getUser().getId()).isEqualTo(2L);
		assertThat(savedGithubAccount.getGithubUserId()).isEqualTo(123L);
		assertThat(savedGithubAccount.getGithubUsername()).isEqualTo("octocat");
	}

	@Test
	void exchangeGithubAuthorizationCode_returnsExistingUserWhenSignUpPayloadIsAlreadyConnectedToActiveUser() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		GithubAccount githubAccount = githubAccount(user, 123L, "octocat");
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 5);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(123L)).thenReturn(Optional.of(githubAccount));
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		verify(userRepository, never()).save(any());
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void createGithubAuthorizationCode_throwsWhenGithubUserIdIsMissing() {
		OAuth2User oAuth2User = githubOAuth2UserWithoutId("octocat", "test@email.com");

		assertThatThrownBy(() -> githubOAuthService.createGithubAuthorizationCode(oAuth2User, "github-access-token"))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_GITHUB_OAUTH_USER);
		verifyNoInteractions(redisService);
	}

	private OAuth2User githubOAuth2User(Long id, String login, String email) {
		return new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"id", id,
				"login", login,
				"email", email
			),
			"id"
		);
	}

	private OAuth2User githubOAuth2UserWithoutEmail(Long id, String login) {
		return new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"id", id,
				"login", login
			),
			"id"
		);
	}

	private OAuth2User githubOAuth2UserWithoutId(String login, String email) {
		return new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"login", login,
				"email", email
			),
			"login"
		);
	}

	private Users githubUser(Long id, String email, String nickname, Integer level) {
		Users user = Users.builder()
			.email(email)
			.nickname(nickname)
			.level(level)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private GithubAccount githubAccount(Users user, Long githubUserId, String githubUsername) {
		return GithubAccount.builder()
			.user(user)
			.githubUserId(githubUserId)
			.githubUsername(githubUsername)
			.build();
	}

	private JwtToken jwtToken() {
		return new JwtToken("Bearer", "access-token", "refresh-token", ACCESS_TOKEN_EXPIRES_AT);
	}

	private String signUpPayload(Long githubUserId, String githubUsername, String email) {
		return "SIGN_UP." + githubUserId + "." + encode(githubUsername) + "." + encode(email);
	}

	private String encode(String value) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
