package team.po.feature.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.redis.RedisService;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.dto.SendEmailRequest;
import team.po.feature.user.dto.ValidateAuthNumberRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.EmailNotVerifiedException;
import team.po.feature.user.exception.EmailSendFailedException;
import team.po.feature.user.exception.InvalidEmailAuthCodeException;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
	private static final String EMAIL_AUTH_CODE_KEY_PREFIX = "email-auth-code:signup:";
	private static final String EMAIL_AUTH_FAIL_COUNT_KEY_PREFIX = "email-auth-fail-count:signup:";
	private static final String VERIFIED_EMAIL_KEY_PREFIX = "email-auth-verified:signup:";
	private static final String VERIFIED_VALUE = "true";
	private static final int AUTH_CODE_ORIGIN = 100_000;
	private static final int AUTH_CODE_BOUND = 900_000;
	private static final int MAX_AUTH_CODE_FAILURE_COUNT = 5;

	private final JavaMailSender javaMailSender;
	private final RedisService redisService;
	private final UserRepository userRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	@Value("${spring.mail.username:}")
	private String fromEmail;

	@Value("${spring.mail.auth-code-ttl:PT5M}")
	private Duration authCodeTtl;

	@Value("${spring.mail.verified-ttl:PT10M}")
	private Duration verifiedTtl;

	@Value("${spring.mail.auth-code-subject:TeamPo 이메일 인증번호}")
	private String authCodeSubject;

	public void sendEmail(SendEmailRequest request) {
		String email = normalizeEmail(request.email());
		checkEmailDuplication(email);

		String authCode = createAuthCode();
		String authCodeKey = createEmailAuthCodeKey(email);
		redisService.setValue(authCodeKey, authCode, authCodeTtl);
		redisService.deleteValue(createAuthFailCountKey(email));
		redisService.deleteValue(createVerifiedEmailKey(email));

		try {
			javaMailSender.send(createAuthCodeMessage(email, authCode));
		} catch (MailException exception) {
			redisService.deleteValue(authCodeKey);
			throw new EmailSendFailedException(
				HttpStatus.BAD_GATEWAY,
				ErrorCodeConstants.EMAIL_SEND_FAILED,
				"인증번호 이메일 발송에 실패했습니다.",
				exception
			);
		}

		log.info("이메일 전송 성공. emailHash={}", hashEmail(email));
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

		redisService.setValue(createVerifiedEmailKey(email), VERIFIED_VALUE, verifiedTtl);
		redisService.deleteValue(authCodeKey);
		redisService.deleteValue(failCountKey);
		log.info("이메일 검증 성공. emailHash={}", hashEmail(email));
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
			redisService.expire(failCountKey, authCodeTtl);
		}

		if (failCount != null && failCount >= MAX_AUTH_CODE_FAILURE_COUNT) {
			redisService.deleteValue(authCodeKey);
			redisService.deleteValue(failCountKey);
			log.info("이메일 인증번호 실패 횟수 초과. emailHash={}", hashEmail(email));
		}
	}

	private String createAuthCode() {
		return String.valueOf(secureRandom.nextInt(AUTH_CODE_BOUND) + AUTH_CODE_ORIGIN);
	}

	private SimpleMailMessage createAuthCodeMessage(String email, String authCode) {
		SimpleMailMessage message = new SimpleMailMessage();
		if (StringUtils.hasText(fromEmail)) {
			message.setFrom(fromEmail);
		}
		message.setTo(email);
		message.setSubject(authCodeSubject);
		message.setText(createAuthCodeText(authCode));

		return message;
	}

	private String createAuthCodeText(String authCode) {
		return """
			TeamPo 이메일 인증번호입니다.

			인증번호: %s

			인증번호는 %d분 동안 유효합니다.
			본인이 요청하지 않았다면 이 메일을 무시해주세요.
			""".formatted(authCode, authCodeTtl.toMinutes());
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
