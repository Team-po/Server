package team.po.feature.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.redis.RedisService;
import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.config.PasswordResetProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.match.service.MatchService;
import team.po.feature.user.domain.GithubAccount;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.RequestPasswordResetRequest;
import team.po.feature.user.dto.RefreshTokenRequest;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.ResetPasswordRequest;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.dto.ValidateDeleteUserEmailRequest;
import team.po.feature.user.repository.GithubAccountRepository;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
	private static final String PASSWORD_RESET_TOKEN_KEY_PREFIX = "password-reset-token:";
	private static final String PASSWORD_RESET_USER_TOKEN_KEY_PREFIX = "password-reset-user-token:";
	private static final String PASSWORD_RESET_PENDING_TOKEN_KEY_PREFIX = "password-reset-pending-token:";
	private static final String PASSWORD_RESET_SESSION_VERSION_KEY_PREFIX = "password-reset-session-version:";
	private static final String PASSWORD_RESET_REQUEST_COOLDOWN_KEY_PREFIX = "password-reset-request-cooldown:";
	private static final int PASSWORD_RESET_TOKEN_BYTE_LENGTH = 32;

	private final UserRepository userRepository;
	private final GithubAccountRepository githubAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;
	private final ProfileImageRedisService profileImageRedisService;
	private final EmailService emailService;
	private final RedisService redisService;
	private final PasswordResetProperties passwordResetProperties;
	private final MatchService matchService;
	private final SecureRandom secureRandom = new SecureRandom();
	@Value("${cloud.aws.s3.endpoint:}")
	private String s3Endpoint;
	@Value("${cloud.aws.s3.bucket}")
	private String bucket;
	@Value("${cloud.aws.region.static}")
	private String region;

	public void signUp(SignUpRequest signUpRequest) {
		String normalizedEmail = this.normalizeEmail(signUpRequest.email());
		this.checkEmailDuplication(normalizedEmail);
		if (signUpRequest.profileImageKey() != null) {
			profileImageRedisService.consumeSignUpTicket(signUpRequest.profileImageKey());
		}
		emailService.consumeVerifiedSignUpEmail(normalizedEmail);
		String password = passwordEncoder.encode(signUpRequest.password());

		Users user = Users.builder().email(normalizedEmail).password(password)
			.profileImage(signUpRequest.profileImageKey()).nickname(signUpRequest.nickname()).description(null)
			.level(signUpRequest.level()).temperature(50).build(); // temperature는 기본값

		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			if (isEmailUniqueConstraintViolation(e)) {
				throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
			}
			throw e;
		}
	}

	public SignInResponse signIn(SignInRequest request) {
		String normalizedEmail = this.normalizeEmail(request.email());

		try {
			Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
			);
			UserPrincipal principal = (UserPrincipal)authentication.getPrincipal();

			JwtToken jwtToken = jwtTokenProvider.generateToken(principal.id(), principal.email());

			return new SignInResponse(
				jwtToken.accessToken(),
				jwtToken.refreshToken(),
				jwtToken.accessTokenExpiresAt()
			);
		} catch (org.springframework.security.core.AuthenticationException exception) {
			throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.", exception);
		}
	}

	public void requestPasswordReset(RequestPasswordResetRequest request) {
		String normalizedEmail = this.normalizeEmail(request.email());
		String cooldownKey = createPasswordResetRequestCooldownKey(normalizedEmail);
		boolean canIssueResetEmail = redisService.setIfAbsentValue(
			cooldownKey,
			"true",
			passwordResetProperties.requestCooldown()
		);

		Users user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
		String token = createPasswordResetToken();
		String tokenHash = sha256Hex(token);

		if (!canIssueResetEmail || isPasswordResetUnavailable(user)) {
			return;
		}

		String tokenKey = createPasswordResetTokenKey(tokenHash);
		String userTokenKey = createPasswordResetUserTokenKey(user.getId());
		String pendingTokenKey = createPasswordResetPendingTokenKey(user.getId());
		String previousTokenHash = redisService.getStringValue(userTokenKey);
		redisService.setValue(
			tokenKey,
			createPasswordResetTokenPayload(user.getId(), getCurrentPasswordResetSessionVersion(user.getId())),
			passwordResetProperties.tokenTtl()
		);
		redisService.setValue(pendingTokenKey, tokenHash, passwordResetProperties.tokenTtl());

		try {
			CompletableFuture<Void> delivery = emailService.sendPasswordResetEmailAsync(
				user.getEmail(),
				createPasswordResetUrl(token)
			);
			delivery.whenComplete((ignored, exception) -> completePasswordResetEmailDelivery(
				tokenKey,
				userTokenKey,
				pendingTokenKey,
				tokenHash,
				previousTokenHash,
				cooldownKey,
				exception
			));
		} catch (RuntimeException exception) {
			cleanupFailedPasswordResetEmail(tokenKey, pendingTokenKey, tokenHash, cooldownKey, exception);
		}
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		String tokenHash = sha256Hex(request.token().trim());
		String tokenPayloadValue = redisService.getAndDeleteStringValue(createPasswordResetTokenKey(tokenHash));

		if (tokenPayloadValue == null) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}

		PasswordResetTokenPayload tokenPayload = parsePasswordResetTokenPayload(tokenPayloadValue);
		Long userId = tokenPayload.userId();
		long sessionVersion = tokenPayload.sessionVersion();
		if (sessionVersion != getCurrentPasswordResetSessionVersion(userId)) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}

		String userTokenKey = createPasswordResetUserTokenKey(userId);
		String currentTokenHash = redisService.getStringValue(userTokenKey);
		if (!tokenHash.equals(currentTokenHash)) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}
		Users user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

		if (sessionVersion != getCurrentPasswordResetSessionVersion(userId)) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}

		if (isPasswordResetUnavailable(user)) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}

		String newPassword = passwordEncoder.encode(request.newPassword());
		user.editPassword(newPassword);
		revokePasswordResetTokens(user.getId());
		jwtTokenProvider.deleteRefreshToken(user.getEmail());
		jwtTokenProvider.revokeAccessTokens(user.getId());
	}

	public void checkEmailDuplication(String email) {
		String normalizedEmail = this.normalizeEmail(email);

		if (userRepository.existsByEmail(normalizedEmail))
			throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);

	}

	public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
		String token = request.refreshToken();
		if (!jwtTokenProvider.validateRefreshToken(token)) {
			throw new ApplicationException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
		}

		Long userId = jwtTokenProvider.getUserId(token);
		String email = jwtTokenProvider.getEmail(token);

		Users user = userRepository.findById(userId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.UNEXISTED_USER, "존재하지 않는 유저의 리프레시 토큰입니다."));

		if (user.getDeletedAt() != null) {
			throw new ApplicationException(ErrorCode.UNEXISTED_USER, "존재하지 않는 유저의 리프레시 토큰입니다.");
		}

		if (!jwtTokenProvider.isRefreshTokenMatched(email, token)) {
			throw new ApplicationException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
		}

		long refreshTokenSessionVersion = jwtTokenProvider.getSessionVersion(token);
		boolean legacyRefreshToken = !jwtTokenProvider.hasSessionVersion(token);
		if (!jwtTokenProvider.isAccessTokenSessionVersionCurrent(
			userId,
			refreshTokenSessionVersion,
			legacyRefreshToken
		)) {
			throw new ApplicationException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
		}

		String accessToken = jwtTokenProvider.generateAccessToken(
			userId,
			user.getEmail(),
			refreshTokenSessionVersion
		);
		return new RefreshTokenResponse(accessToken, jwtTokenProvider.getExpiration(accessToken));
	}

	public GetProfileResponse getMyProfile(Users user) {
		GithubAccount githubAccount = githubAccountRepository.findByUserIdAndDeletedAtIsNull(user.getId())
			.orElse(null);

		return GetProfileResponse.builder()
			.email(user.getEmail())
			.nickname(user.getNickname())
			.temperature(user.getTemperature())
			.level(user.getLevel())
			.description(user.getDescription())
			.profileImage(buildProfileImageUrl(user.getProfileImage()))
			.isGithubLogin(user.isGithubLogin())
			.isGithubLinked(githubAccount != null)
			.githubUsername(githubAccount == null ? null : githubAccount.getGithubUsername())
			.build();
	}

	@Transactional
	public void editMyProfile(Users loginUser, EditProfileRequest request) {
		if (request.profileImageKey() != null) {
			profileImageRedisService.consumeProfileUpdateTicket(loginUser.getId(), request.profileImageKey());
		}
		loginUser.editDescription(request.description());
		loginUser.editLevel(request.level());
		loginUser.editNickname(request.nickname());
		if (request.profileImageKey() != null) {
			loginUser.editProfileImage(request.profileImageKey());
		}
	}

	@Transactional
	public void editPassword(Users loginUser, EditPasswordRequest request) {
		Users user = userRepository.findByIdAndDeletedAtIsNullForUpdate(loginUser.getId()).orElseThrow(
			() -> new ApplicationException(ErrorCode.UNEXISTED_USER));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword()))
			throw new ApplicationException(ErrorCode.UNMATCHED_PASSWORD);

		String newPassword = passwordEncoder.encode(request.afterPassword());
		user.editPassword(newPassword);
		revokePasswordResetTokens(user.getId());
		jwtTokenProvider.deleteRefreshToken(user.getEmail());
		jwtTokenProvider.revokeAccessTokens(user.getId());
	}

	public void sendDeleteUserEmail(Users loginUser) {
		Users user = this.getActiveUser(loginUser.getId());

		emailService.sendDeleteUserEmail(user.getEmail());
	}

	public void validateDeleteUserEmail(Users loginUser, ValidateDeleteUserEmailRequest request) {
		Users user = this.getActiveUser(loginUser.getId());

		emailService.validateDeleteUserAuthNumber(user.getEmail(), request.authNumber());
	}

	@Transactional
	public void deleteUser(Users loginUser) {
		Users user = this.getActiveUser(loginUser.getId());
		emailService.validateVerifiedDeleteUserEmail(user.getEmail());
		matchService.cancelActiveMatchForWithdrawal(user.getId());

		Instant deletedAt = Instant.now();
		String email = user.getEmail();
		String deletedEmail = createDeletedEmail(user.getId(), email, deletedAt);

		user.softDelete(deletedAt, deletedEmail);
		githubAccountRepository.findByUserIdAndDeletedAtIsNull(user.getId())
			.ifPresent(githubAccount -> githubAccount.softDelete(deletedAt));
		userRepository.flush();
		jwtTokenProvider.deleteRefreshToken(email);
		jwtTokenProvider.revokeAccessTokens(user.getId());
		emailService.consumeVerifiedDeleteUserEmail(email);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;

		while (cause != null) {
			String message = cause.getMessage();
			if (message != null && message.contains("uq_users_email")) {
				return true;
			}
			cause = cause.getCause();
		}

		return false;
	}

	private String createDeletedEmail(Long userId, String email, Instant deletedAt) {
		return "deleted__" + userId + "__" + deletedAt.toEpochMilli() + "__" + hashEmail(email);
	}

	private String hashEmail(String email) {
		return sha256Hex(email);
	}

	private String sha256Hex(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
		}
	}

	private String createPasswordResetToken() {
		byte[] bytes = new byte[PASSWORD_RESET_TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String createPasswordResetUrl(String token) {
		return UriComponentsBuilder.fromUriString(passwordResetProperties.clientResetUrl())
			.fragment("token=" + token)
			.build()
			.toUriString();
	}

	private void completePasswordResetEmailDelivery(
		String tokenKey,
		String userTokenKey,
		String pendingTokenKey,
		String tokenHash,
		String previousTokenHash,
		String cooldownKey,
		Throwable exception
	) {
		if (exception != null) {
			cleanupFailedPasswordResetEmail(tokenKey, pendingTokenKey, tokenHash, cooldownKey, exception);
			return;
		}

		try {
			if (!isLatestPasswordResetRequest(pendingTokenKey, tokenHash)) {
				redisService.deleteValue(tokenKey);
				return;
			}

			redisService.setValue(userTokenKey, tokenHash, passwordResetProperties.tokenTtl());
			if (previousTokenHash != null) {
				redisService.deleteValue(createPasswordResetTokenKey(previousTokenHash));
			}
			redisService.deleteValue(pendingTokenKey);
		} catch (RuntimeException cleanupException) {
			log.warn(
				"Password reset token finalization failed: {}",
				cleanupException.getClass().getSimpleName()
			);
		}
	}

	private void cleanupFailedPasswordResetEmail(
		String tokenKey,
		String pendingTokenKey,
		String tokenHash,
		String cooldownKey,
		Throwable exception
	) {
		redisService.deleteValue(tokenKey);
		if (isLatestPasswordResetRequest(pendingTokenKey, tokenHash)) {
			redisService.deleteValue(pendingTokenKey);
			redisService.deleteValue(cooldownKey);
		}
		log.warn("Password reset email delivery failed: {}", exception.getClass().getSimpleName());
	}

	private boolean isLatestPasswordResetRequest(String pendingTokenKey, String tokenHash) {
		return tokenHash.equals(redisService.getStringValue(pendingTokenKey));
	}

	private void revokePasswordResetTokens(Long userId) {
		String userTokenKey = createPasswordResetUserTokenKey(userId);
		String tokenHash = redisService.getStringValue(userTokenKey);
		if (tokenHash != null) {
			redisService.deleteValue(createPasswordResetTokenKey(tokenHash));
		}

		String pendingTokenKey = createPasswordResetPendingTokenKey(userId);
		String pendingTokenHash = redisService.getStringValue(pendingTokenKey);
		if (pendingTokenHash != null) {
			redisService.deleteValue(createPasswordResetTokenKey(pendingTokenHash));
		}

		redisService.deleteValue(userTokenKey);
		redisService.deleteValue(pendingTokenKey);
		redisService.incrementValue(createPasswordResetSessionVersionKey(userId));
	}

	private String createPasswordResetTokenPayload(Long userId, long sessionVersion) {
		return userId + ":" + sessionVersion;
	}

	private long getCurrentPasswordResetSessionVersion(Long userId) {
		String version = redisService.getStringValue(createPasswordResetSessionVersionKey(userId));
		if (version == null) {
			return 0L;
		}

		return Long.parseLong(version);
	}

	private String createPasswordResetTokenKey(String tokenHash) {
		return PASSWORD_RESET_TOKEN_KEY_PREFIX + tokenHash;
	}

	private String createPasswordResetUserTokenKey(Long userId) {
		return PASSWORD_RESET_USER_TOKEN_KEY_PREFIX + userId;
	}

	private String createPasswordResetPendingTokenKey(Long userId) {
		return PASSWORD_RESET_PENDING_TOKEN_KEY_PREFIX + userId;
	}

	private String createPasswordResetSessionVersionKey(Long userId) {
		return PASSWORD_RESET_SESSION_VERSION_KEY_PREFIX + userId;
	}

	private String createPasswordResetRequestCooldownKey(String email) {
		return PASSWORD_RESET_REQUEST_COOLDOWN_KEY_PREFIX + hashEmail(email);
	}

	private PasswordResetTokenPayload parsePasswordResetTokenPayload(String tokenPayloadValue) {
		String[] parts = tokenPayloadValue.split(":", -1);
		if (parts.length != 2) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
		}

		try {
			return new PasswordResetTokenPayload(Long.valueOf(parts[0]), Long.parseLong(parts[1]));
		} catch (NumberFormatException exception) {
			throw new ApplicationException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception);
		}
	}

	private boolean isPasswordResetUnavailable(Users user) {
		return user == null || user.isGithubLogin() || !StringUtils.hasText(user.getPassword());
	}

	private String buildProfileImageUrl(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			return objectKey;
		}
		String normalizedObjectKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;

		if (s3Endpoint == null || s3Endpoint.isBlank()) {
			return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + normalizedObjectKey;
		}

		String normalizedEndpoint = s3Endpoint.endsWith("/") ? s3Endpoint.substring(0, s3Endpoint.length() - 1) : s3Endpoint;

		return normalizedEndpoint + "/" + bucket + "/" + normalizedObjectKey;
	}

	private Users getActiveUser(Long id) {
		return  userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(
			() -> new ApplicationException(ErrorCode.UNEXISTED_USER));
	}

	private record PasswordResetTokenPayload(Long userId, long sessionVersion) {
	}
}
