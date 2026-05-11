package team.po.feature.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import team.po.common.redis.RedisService;
import team.po.config.EmailAuthProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.user.dto.SendEmailRequest;
import team.po.feature.user.dto.ValidateAuthNumberRequest;
import team.po.feature.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class EmailService {
	private static final String EMAIL_AUTH_CODE_KEY_PREFIX = "email-auth-code:";
	private static final String EMAIL_AUTH_FAIL_COUNT_KEY_PREFIX = "email-auth-fail-count:";
	private static final String VERIFIED_EMAIL_KEY_PREFIX = "email-auth-verified:";
	private static final String VERIFIED_VALUE = "true";
	private static final String EMAIL_VERIFICATION_TEMPLATE_PATH = "templates/email-verification.html";
	private static final String VERIFICATION_CODE_PLACEHOLDER = "__VERIFICATION_CODE__";
	private static final String VERIFICATION_GUIDE_MESSAGE_PLACEHOLDER = "__VERIFICATION_GUIDE_MESSAGE__";
	private static final int AUTH_CODE_ORIGIN = 100_000;
	private static final int AUTH_CODE_BOUND = 900_000;
	private static final int MAX_AUTH_CODE_FAILURE_COUNT = 5;

	private final JavaMailSender javaMailSender;
	private final RedisService redisService;
	private final UserRepository userRepository;
	private final EmailAuthProperties emailAuthProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public void sendEmail(SendEmailRequest request) {
		String email = normalizeEmail(request.email());
		checkEmailDuplication(email);

		sendAuthCodeEmail(email, EmailAuthPurpose.SIGN_UP);
	}

	public void sendDeleteUserEmail(String email) {
		sendAuthCodeEmail(normalizeEmail(email), EmailAuthPurpose.DELETE_USER);
	}

	private void sendAuthCodeEmail(String email, EmailAuthPurpose purpose) {
		String authCode = createAuthCode();
		String authCodeKey = createEmailAuthCodeKey(email, purpose);
		redisService.setValue(authCodeKey, authCode, emailAuthProperties.authCodeTtl());
		redisService.deleteValue(createAuthFailCountKey(email, purpose));
		redisService.deleteValue(createVerifiedEmailKey(email, purpose));

		try {
			javaMailSender.send(createAuthCodeMessage(email, authCode, purpose.guideMessage));
		} catch (MailException | MessagingException | IOException exception) {
			redisService.deleteValue(authCodeKey);
			throw new ApplicationException(
				ErrorCode.EMAIL_SEND_FAILED,
				ErrorCode.EMAIL_SEND_FAILED.getMessage(),
				exception
			);
		}
	}

	public void validateAuthNumber(ValidateAuthNumberRequest request) {
		String email = normalizeEmail(request.email());
		validateAuthNumber(email, request.authNumber(), EmailAuthPurpose.SIGN_UP);
	}

	public void validateDeleteUserAuthNumber(String email, Integer authNumber) {
		validateAuthNumber(normalizeEmail(email), authNumber, EmailAuthPurpose.DELETE_USER);
	}

	private void validateAuthNumber(String email, Integer authNumber, EmailAuthPurpose purpose) {
		String authCodeKey = createEmailAuthCodeKey(email, purpose);
		String failCountKey = createAuthFailCountKey(email, purpose);
		String savedAuthCode = redisService.getStringValue(authCodeKey);

		if (!String.valueOf(authNumber).equals(savedAuthCode)) {
			recordAuthCodeFailure(authCodeKey, failCountKey);
			throw new ApplicationException(ErrorCode.INVALID_EMAIL_AUTH_CODE);
		}

		redisService.setValue(createVerifiedEmailKey(email, purpose), VERIFIED_VALUE, emailAuthProperties.verifiedTtl());
		redisService.deleteValue(authCodeKey);
		redisService.deleteValue(failCountKey);
	}

	public void consumeVerifiedSignUpEmail(String email) {
		consumeVerifiedEmail(email, EmailAuthPurpose.SIGN_UP);
	}

	public void consumeVerifiedDeleteUserEmail(String email) {
		consumeVerifiedEmail(email, EmailAuthPurpose.DELETE_USER);
	}

	private void consumeVerifiedEmail(String email, EmailAuthPurpose purpose) {
		String normalizedEmail = normalizeEmail(email);
		Object verified = redisService.getAndDeleteValue(createVerifiedEmailKey(normalizedEmail, purpose));

		if (!VERIFIED_VALUE.equals(verified)) {
			throw new ApplicationException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
	}

	private void checkEmailDuplication(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
	}

	private void recordAuthCodeFailure(String authCodeKey, String failCountKey) {
		Long failCount = redisService.incrementValue(failCountKey);
		if (Long.valueOf(1L).equals(failCount)) {
			redisService.expire(failCountKey, emailAuthProperties.authCodeTtl());
		}

		if (failCount != null && failCount >= MAX_AUTH_CODE_FAILURE_COUNT) {
			redisService.deleteValue(authCodeKey);
			redisService.deleteValue(failCountKey);
		}
	}

	private String createAuthCode() {
		return String.valueOf(secureRandom.nextInt(AUTH_CODE_BOUND) + AUTH_CODE_ORIGIN);
	}

	private MimeMessage createAuthCodeMessage(String email, String authCode, String guideMessage)
		throws MessagingException, IOException {
		MimeMessage message = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
		if (StringUtils.hasText(emailAuthProperties.username())) {
			helper.setFrom(emailAuthProperties.username());
		}
		helper.setTo(email);
		helper.setSubject(emailAuthProperties.authCodeSubject());
		helper.setText(createAuthCodeHtml(authCode, guideMessage), true);

		return message;
	}

	private String createAuthCodeHtml(String authCode, String guideMessage) throws IOException {
		Resource template = new ClassPathResource(EMAIL_VERIFICATION_TEMPLATE_PATH);
		String html;
		try (InputStream inputStream = template.getInputStream()) {
			html = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
		return html
			.replace(VERIFICATION_CODE_PLACEHOLDER, authCode)
			.replace(VERIFICATION_GUIDE_MESSAGE_PLACEHOLDER, guideMessage);
	}

	private String createEmailAuthCodeKey(String email, EmailAuthPurpose purpose) {
		return EMAIL_AUTH_CODE_KEY_PREFIX + purpose.key + ":" + hashEmail(email);
	}

	private String createAuthFailCountKey(String email, EmailAuthPurpose purpose) {
		return EMAIL_AUTH_FAIL_COUNT_KEY_PREFIX + purpose.key + ":" + hashEmail(email);
	}

	private String createVerifiedEmailKey(String email, EmailAuthPurpose purpose) {
		return VERIFIED_EMAIL_KEY_PREFIX + purpose.key + ":" + hashEmail(email);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String hashEmail(String email) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
		}
	}

	private enum EmailAuthPurpose {
		SIGN_UP(
			"signup",
			"Team-po 계정 생성을 완료하려면 아래 인증번호를 인증 화면에 입력해 주세요. 인증번호는 발급 후 5분간만 유효합니다."
		),
		DELETE_USER(
			"delete-user",
			"Team-po 계정 삭제를 완료하려면 아래 인증번호를 인증 화면에 입력해 주세요. 인증번호는 발급 후 5분간만 유효합니다."
		);

		private final String key;
		private final String guideMessage;

		EmailAuthPurpose(String key, String guideMessage) {
			this.key = key;
			this.guideMessage = guideMessage;
		}
	}
}
