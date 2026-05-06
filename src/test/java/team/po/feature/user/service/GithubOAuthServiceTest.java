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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

	@InjectMocks
	private GithubOAuthService githubOAuthService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(githubOAuthService, "authorizationCodeTtl", AUTHORIZATION_CODE_TTL);
	}

	@Test
	void createAuthorizationCode_returnsLoginCodeWhenGithubAccountIsConnectedToActiveUser() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		GithubAccount githubAccount = githubAccount(user, 123L, "octocat");
		OAuth2User oAuth2User = githubOAuth2User(123L, "octocat", " Test@Email.com ");
		when(githubAccountRepository.findByGithubUserId(123L)).thenReturn(Optional.of(githubAccount));

		GithubOAuthService.GithubAuthorizationCode authorizationCode =
			githubOAuthService.createAuthorizationCode(oAuth2User, "github-access-token");

		assertThat(authorizationCode.code()).isNotBlank();
		assertThat(authorizationCode.onboardingRequired()).isFalse();
		verify(redisService).setValue(
			eq("github-oauth-code:" + authorizationCode.code()),
			eq("LOGIN.1"),
			eq(AUTHORIZATION_CODE_TTL)
		);
		verifyNoInteractions(restClient);
	}

	@Test
	void createAuthorizationCode_returnsSignUpCodeWhenGithubAccountDoesNotExist() {
		OAuth2User oAuth2User = githubOAuth2User(123L, "octocat", " Test@Email.com ");
		when(githubAccountRepository.findByGithubUserId(123L)).thenReturn(Optional.empty());

		GithubOAuthService.GithubAuthorizationCode authorizationCode =
			githubOAuthService.createAuthorizationCode(oAuth2User, "github-access-token");

		assertThat(authorizationCode.code()).isNotBlank();
		assertThat(authorizationCode.onboardingRequired()).isTrue();

		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(redisService).setValue(
			eq("github-oauth-code:" + authorizationCode.code()),
			payloadCaptor.capture(),
			eq(AUTHORIZATION_CODE_TTL)
		);
		assertThat(payloadCaptor.getValue().toString())
			.isEqualTo(signUpPayload(123L, "octocat", "test@email.com"));
	}

	@Test
	void exchangeAuthorizationCode_returnsTokenWithoutLevelWhenPayloadIsLogin() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY)).thenReturn("LOGIN.1");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.expiresAt()).isEqualTo(ACCESS_TOKEN_EXPIRES_AT);
		verify(githubAccountRepository, never()).save(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void exchangeAuthorizationCode_throwsWhenAuthorizationCodeIsExpiredOrInvalid() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY)).thenReturn(null);

		assertThatThrownBy(() -> githubOAuthService.exchangeAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
	}

	@Test
	void exchangeAuthorizationCode_throwsWhenSignUpPayloadHasNoLevel() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", null);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));

		assertThatThrownBy(() -> githubOAuthService.exchangeAuthorizationCode(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("레벨 선택은 필수입니다.")
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_INPUT_FIELD);
		verifyNoInteractions(userRepository);
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void exchangeAuthorizationCode_createsUserAndGithubAccountWhenPayloadIsSignUp() {
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 4);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserId(123L)).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
			Users savedUser = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedUser, "id", 1L);
			return savedUser;
		});
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeAuthorizationCode(request);

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

		ArgumentCaptor<GithubAccount> githubAccountCaptor = ArgumentCaptor.forClass(GithubAccount.class);
		verify(githubAccountRepository).save(githubAccountCaptor.capture());
		GithubAccount savedGithubAccount = githubAccountCaptor.getValue();
		assertThat(savedGithubAccount.getUser()).isSameAs(savedUser);
		assertThat(savedGithubAccount.getGithubUserId()).isEqualTo(123L);
		assertThat(savedGithubAccount.getGithubUsername()).isEqualTo("octocat");
	}

	@Test
	void exchangeAuthorizationCode_reconnectsGithubAccountWhenPreviousUserWasDeleted() {
		Users deletedUser = githubUser(1L, "deleted-test@email.com", "old-octocat", 2);
		deletedUser.softDelete(Instant.parse("2026-05-06T09:00:00Z"), "deleted-test@email.com");
		GithubAccount githubAccount = githubAccount(deletedUser, 123L, "old-octocat");
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 5);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserId(123L)).thenReturn(Optional.of(githubAccount));
		when(userRepository.save(any(Users.class))).thenAnswer(invocation -> {
			Users savedUser = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedUser, "id", 2L);
			return savedUser;
		});
		when(jwtTokenProvider.generateToken(2L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(githubAccount.getUser().getId()).isEqualTo(2L);
		assertThat(githubAccount.getUser().getEmail()).isEqualTo("test@email.com");
		assertThat(githubAccount.getUser().getLevel()).isEqualTo(5);
		assertThat(githubAccount.getGithubUsername()).isEqualTo("octocat");
		verify(githubAccountRepository, never()).save(any());
		verify(userRepository, never()).existsByEmail(anyString());
	}

	@Test
	void exchangeAuthorizationCode_returnsExistingUserWhenSignUpPayloadIsAlreadyConnectedToActiveUser() {
		Users user = githubUser(1L, "test@email.com", "octocat", 3);
		GithubAccount githubAccount = githubAccount(user, 123L, "octocat");
		OAuthAuthorizationCodeRequest request = new OAuthAuthorizationCodeRequest("authorization-code", 5);
		when(redisService.getAndDeleteStringValue(AUTHORIZATION_CODE_KEY))
			.thenReturn(signUpPayload(123L, "octocat", "test@email.com"));
		when(githubAccountRepository.findByGithubUserId(123L)).thenReturn(Optional.of(githubAccount));
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(jwtToken());

		SignInResponse response = githubOAuthService.exchangeAuthorizationCode(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		verify(userRepository, never()).save(any());
		verify(githubAccountRepository, never()).save(any());
	}

	@Test
	void createAuthorizationCode_throwsWhenGithubUserIdIsMissing() {
		OAuth2User oAuth2User = githubOAuth2UserWithoutId("octocat", "test@email.com");

		assertThatThrownBy(() -> githubOAuthService.createAuthorizationCode(oAuth2User, "github-access-token"))
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
