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
import org.springframework.http.HttpStatus;
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
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.dto.SendEmailRequest;
import team.po.feature.user.dto.ValidateAuthNumberRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.EmailNotVerifiedException;
import team.po.feature.user.exception.EmailSendFailedException;
import team.po.feature.user.exception.InvalidEmailAuthCodeException;
import team.po.feature.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class EmailService {
	private static final String EMAIL_AUTH_CODE_KEY_PREFIX = "email-auth-code:signup:";
	private static final String EMAIL_AUTH_FAIL_COUNT_KEY_PREFIX = "email-auth-fail-count:signup:";
	private static final String VERIFIED_EMAIL_KEY_PREFIX = "email-auth-verified:signup:";
	private static final String VERIFIED_VALUE = "true";
	private static final String EMAIL_VERIFICATION_TEMPLATE_PATH = "templates/email-verification.html";
	private static final String VERIFICATION_CODE_PLACEHOLDER = "__VERIFICATION_CODE__";
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

		String authCode = createAuthCode();
		String authCodeKey = createEmailAuthCodeKey(email);
		redisService.setValue(authCodeKey, authCode, emailAuthProperties.authCodeTtl());
		redisService.deleteValue(createAuthFailCountKey(email));
		redisService.deleteValue(createVerifiedEmailKey(email));

		try {
			javaMailSender.send(createAuthCodeMessage(email, authCode));
		} catch (MailException | MessagingException | IOException exception) {
			redisService.deleteValue(authCodeKey);
			throw new EmailSendFailedException(
				HttpStatus.BAD_GATEWAY,
				ErrorCodeConstants.EMAIL_SEND_FAILED,
				"인증번호 이메일 발송에 실패했습니다.",
				exception
			);
		}
	}

	public void validateAuthNumber(ValidateAuthNumberRequest request) {
		String email = normalizeEmail(request.email());
		String authCodeKey = createEmailAuthCodeKey(email);
		String failCountKey = createAuthFailCountKey(email);
		Object savedAuthCode = redisService.getValue(authCodeKey);

		if (!String.valueOf(request.authNumber()).equals(savedAuthCode)) {
			recordAuthCodeFailure(email, authCodeKey, failCountKey);
			throw new InvalidEmailAuthCodeException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.INVALID_EMAIL_AUTH_CODE,
				"인증번호가 만료되었거나 올바르지 않습니다."
			);
		}

		redisService.setValue(createVerifiedEmailKey(email), VERIFIED_VALUE, emailAuthProperties.verifiedTtl());
		redisService.deleteValue(authCodeKey);
		redisService.deleteValue(failCountKey);
	}

	public void consumeVerifiedSignUpEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		Object verified = redisService.getAndDeleteValue(createVerifiedEmailKey(normalizedEmail));

		if (!VERIFIED_VALUE.equals(verified)) {
			throw new EmailNotVerifiedException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.EMAIL_NOT_VERIFIED,
				"이메일 인증이 필요합니다."
			);
		}
	}

	private void checkEmailDuplication(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new DuplicatedEmailException(
				HttpStatus.CONFLICT,
				ErrorCodeConstants.EMAIL_ALREADY_EXISTS,
				"중복된 이메일이 존재합니다."
			);
		}
	}

	private void recordAuthCodeFailure(String email, String authCodeKey, String failCountKey) {
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

	private MimeMessage createAuthCodeMessage(String email, String authCode) throws MessagingException, IOException {
		MimeMessage message = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
		if (StringUtils.hasText(emailAuthProperties.username())) {
			helper.setFrom(emailAuthProperties.username());
		}
		helper.setTo(email);
		helper.setSubject(emailAuthProperties.authCodeSubject());
		helper.setText(createAuthCodeHtml(authCode), true);

		return message;
	}

	private String createAuthCodeHtml(String authCode) throws IOException {
		Resource template = new ClassPathResource(EMAIL_VERIFICATION_TEMPLATE_PATH);
		String html;
		try (InputStream inputStream = template.getInputStream()) {
			html = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
		}
		return html.replace(VERIFICATION_CODE_PLACEHOLDER, authCode);
	}

	private String createEmailAuthCodeKey(String email) {
		return EMAIL_AUTH_CODE_KEY_PREFIX + hashEmail(email);
	}

	private String createAuthFailCountKey(String email) {
		return EMAIL_AUTH_FAIL_COUNT_KEY_PREFIX + hashEmail(email);
	}

	private String createVerifiedEmailKey(String email) {
		return VERIFIED_EMAIL_KEY_PREFIX + hashEmail(email);
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
}
