package team.po.feature.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class GithubOAuthService {
	private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
	private static final String AUTHORIZATION_CODE_PREFIX = "github-oauth-code:";

	private final GithubAccountRepository githubAccountRepository;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RedisService redisService;
	private final RestClient restClient;

	@Value("${github.oauth.authorization-code-ttl}")
	private Duration authorizationCodeTtl;

	public GithubAuthorizationCode createAuthorizationCode(OAuth2User oAuth2User, String githubAccessToken) {
		Long githubUserId = getGithubUserId(oAuth2User);
		String githubUsername = getGithubNickname(oAuth2User);
		String email = getGithubEmail(oAuth2User, githubAccessToken);
		GithubAuthorizationPayload payload = createAuthorizationPayload(githubUserId, githubUsername, email);

		String authorizationCode = UUID.randomUUID().toString();
		redisService.setValue(
			createAuthorizationCodeKey(authorizationCode),
			payload.serialize(),
			authorizationCodeTtl
		);
		return new GithubAuthorizationCode(authorizationCode, payload.requiresOnboarding());
	}

	@Transactional
	public SignInResponse exchangeAuthorizationCode(OAuthAuthorizationCodeRequest request) {
		String payloadValue = redisService.getAndDeleteStringValue(createAuthorizationCodeKey(request.code()));
		if (payloadValue == null) {
			throw new ApplicationException(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
		}

		GithubAuthorizationPayload payload = GithubAuthorizationPayload.deserialize(payloadValue);
		Users user = switch (payload.type()) {
			case LOGIN -> getLoginUser(payload);
			case SIGN_UP -> signUpGithubUser(payload, request.level());
		};

		JwtToken jwtToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
		return new SignInResponse(jwtToken.accessToken(), jwtToken.refreshToken(), jwtToken.accessTokenExpiresAt());
	}

	private GithubAuthorizationPayload createAuthorizationPayload(Long githubUserId, String githubUsername, String email) {
		return githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(githubUserId)
			.map(githubAccount -> GithubAuthorizationPayload.login(githubAccount.getUser().getId()))
			.orElseGet(() -> GithubAuthorizationPayload.signUp(githubUserId, githubUsername, email));
	}

	private Users getLoginUser(GithubAuthorizationPayload payload) {
		return userRepository.findByIdAndDeletedAtIsNull(payload.userId())
			.orElseThrow(() -> new ApplicationException(ErrorCode.GITHUB_LOGIN_USER_NOT_FOUND));
	}

	private Users signUpGithubUser(GithubAuthorizationPayload payload, Integer level) {
		validateRequiredLevel(level);
		return githubAccountRepository.findByGithubUserIdAndDeletedAtIsNull(payload.githubUserId())
			.map(GithubAccount::getUser)
			.orElseGet(() -> createGithubUserAndAccount(payload, level));
	}

	private Users createGithubUserAndAccount(GithubAuthorizationPayload payload, Integer level) {
		if (userRepository.existsByEmail(payload.email())) {
			throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS, "이미 가입된 이메일입니다.");
		}

		Users savedUser = createGithubUser(payload, level);

		try {
			githubAccountRepository.save(GithubAccount.builder()
				.user(savedUser)
				.githubUserId(payload.githubUserId())
				.githubUsername(payload.githubUsername())
				.build());
		} catch (DataIntegrityViolationException exception) {
			throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS, "이미 연결된 GitHub 계정입니다.", exception);
		}

		return savedUser;
	}

	private void validateRequiredLevel(Integer level) {
		if (level == null) {
			throw new ApplicationException(ErrorCode.INVALID_INPUT_FIELD, "레벨 선택은 필수입니다.");
		}
	}

	private Users createGithubUser(GithubAuthorizationPayload payload, Integer level) {
		Users user = Users.builder()
			.email(payload.email())
			.nickname(payload.githubUsername())
			.level(level)
			.temperature(50)
			.build();

		try {
			return userRepository.save(user);
		} catch (DataIntegrityViolationException exception) {
			throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS, "이미 가입된 이메일입니다.", exception);
		}
	}

	private String getGithubEmail(OAuth2User oAuth2User, String githubAccessToken) {
		String email = getStringAttribute(oAuth2User, "email");
		if (StringUtils.hasText(email)) {
			return normalizeEmail(email);
		}

		GithubEmail[] emails = restClient.get()
			.uri(GITHUB_EMAILS_URL)
			.header("Authorization", "Bearer " + githubAccessToken)
			.header("Accept", "application/vnd.github+json")
			.retrieve()
			.body(GithubEmail[].class);

		if (emails == null) {
			throw new ApplicationException(ErrorCode.GITHUB_EMAIL_NOT_FOUND);
		}

		return Arrays.stream(emails)
			.filter(emailResponse -> emailResponse.primary() && emailResponse.verified())
			.map(GithubEmail::email)
			.filter(StringUtils::hasText)
			.map(this::normalizeEmail)
			.findFirst()
			.orElseThrow(() -> new ApplicationException(ErrorCode.GITHUB_EMAIL_NOT_FOUND));
	}

	private Long getGithubUserId(OAuth2User oAuth2User) {
		Object value = oAuth2User.getAttribute("id");
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new ApplicationException(ErrorCode.INVALID_GITHUB_OAUTH_USER);
	}

	private String getGithubNickname(OAuth2User oAuth2User) {
		String nickname = getStringAttribute(oAuth2User, "login");
		if (!StringUtils.hasText(nickname)) {
			throw new ApplicationException(ErrorCode.INVALID_GITHUB_OAUTH_USER);
		}
		return nickname;
	}

	private String getStringAttribute(OAuth2User oAuth2User, String attributeName) {
		Object value = oAuth2User.getAttribute(attributeName);
		return value == null ? null : value.toString();
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String createAuthorizationCodeKey(String authorizationCode) {
		return AUTHORIZATION_CODE_PREFIX + authorizationCode;
	}

	private record GithubEmail(
		String email,
		boolean primary,
		boolean verified,
		@JsonProperty("visibility")
		String visibility
	) {
	}

	private record GithubAuthorizationPayload(
		GithubAuthorizationType type,
		Long userId,
		Long githubUserId,
		String githubUsername,
		String email
	) {
		private static final String DELIMITER = ".";

		private static GithubAuthorizationPayload login(Long userId) {
			return new GithubAuthorizationPayload(GithubAuthorizationType.LOGIN, userId, null, null, null);
		}

		private static GithubAuthorizationPayload signUp(Long githubUserId, String githubUsername, String email) {
			return new GithubAuthorizationPayload(GithubAuthorizationType.SIGN_UP, null, githubUserId, githubUsername, email);
		}

		private boolean requiresOnboarding() {
			return type == GithubAuthorizationType.SIGN_UP;
		}

		private String serialize() {
			return switch (type) {
				case LOGIN -> type.name() + DELIMITER + userId;
				case SIGN_UP -> type.name() + DELIMITER + githubUserId + DELIMITER + encode(githubUsername) + DELIMITER + encode(email);
			};
		}

		private static GithubAuthorizationPayload deserialize(String value) {
			String[] tokens = value.split("\\.", 4);
			if (tokens.length < 2) {
				throw new ApplicationException(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
			}

			try {
				GithubAuthorizationType type = GithubAuthorizationType.valueOf(tokens[0]);
				return switch (type) {
					case LOGIN -> deserializeLogin(tokens);
					case SIGN_UP -> deserializeSignUp(tokens);
				};
			} catch (IllegalArgumentException exception) {
				throw new ApplicationException(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE, exception);
			}
		}

		private static GithubAuthorizationPayload deserializeLogin(String[] tokens) {
			if (tokens.length != 2) {
				throw new ApplicationException(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
			}
			return login(Long.valueOf(tokens[1]));
		}

		private static GithubAuthorizationPayload deserializeSignUp(String[] tokens) {
			if (tokens.length != 4) {
				throw new ApplicationException(ErrorCode.INVALID_OAUTH_AUTHORIZATION_CODE);
			}
			return signUp(Long.valueOf(tokens[1]), decode(tokens[2]), decode(tokens[3]));
		}

		private static String encode(String value) {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
		}

		private static String decode(String value) {
			return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
		}
	}

	private enum GithubAuthorizationType {
		LOGIN,
		SIGN_UP
	}
}
