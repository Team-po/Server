package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import team.po.common.redis.RedisService;
import team.po.feature.user.dto.SendEmailRequest;
import team.po.feature.user.dto.ValidateAuthNumberRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.EmailNotVerifiedException;
import team.po.feature.user.exception.EmailSendFailedException;
import team.po.feature.user.exception.InvalidEmailAuthCodeException;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
	private static final Duration AUTH_CODE_TTL = Duration.ofMinutes(5);
	private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

	@Mock
	private JavaMailSender javaMailSender;

	@Mock
	private RedisService redisService;

	@Mock
	private UserRepository userRepository;

	private EmailService emailService;
	private MimeMessage mimeMessage;

	@BeforeEach
	void setUp() {
		emailService = new EmailService(javaMailSender, redisService, userRepository);
		mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
		lenient().when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
		ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@teampo.com");
		ReflectionTestUtils.setField(emailService, "authCodeTtl", AUTH_CODE_TTL);
		ReflectionTestUtils.setField(emailService, "verifiedTtl", VERIFIED_TTL);
		ReflectionTestUtils.setField(emailService, "authCodeSubject", "TeamPo 이메일 인증번호");
	}

	@Test
	void sendEmail_sendsAuthCodeHtmlAndStoresItWithTtl() throws Exception {
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);

		emailService.sendEmail(new SendEmailRequest(" Test@Email.com "));

		ArgumentCaptor<String> authCodeCaptor = ArgumentCaptor.forClass(String.class);
		verify(redisService).setValue(
			eq(emailAuthCodeKey("test@email.com")),
			authCodeCaptor.capture(),
			eq(AUTH_CODE_TTL)
		);
		verify(redisService).deleteValue(emailAuthFailCountKey("test@email.com"));
		verify(redisService).deleteValue(emailVerifiedKey("test@email.com"));

		String authCode = authCodeCaptor.getValue();
		assertThat(authCode).matches("\\d{6}");

		verify(javaMailSender).send(mimeMessage);
		mimeMessage.saveChanges();
		assertThat(mimeMessage.getFrom()[0].toString()).isEqualTo("no-reply@teampo.com");
		assertThat(mimeMessage.getRecipients(MimeMessage.RecipientType.TO)[0].toString()).isEqualTo("test@email.com");
		assertThat(mimeMessage.getSubject()).isEqualTo("TeamPo 이메일 인증번호");
		assertThat(mimeMessage.getContentType()).contains("text/html");
		assertThat(mimeMessage.getContent().toString())
			.contains(authCode)
			.doesNotContain("__VERIFICATION_CODE__");
	}

	@Test
	void sendEmail_throwsWhenEmailAlreadyExists() {
		when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

		assertThatThrownBy(() -> emailService.sendEmail(new SendEmailRequest(" Test@Email.com ")))
			.isInstanceOf(DuplicatedEmailException.class)
			.hasMessage("중복된 이메일이 존재합니다.");

		verifyNoInteractions(redisService, javaMailSender);
	}

	@Test
	void sendEmail_deletesAuthCodeWhenMailSendFails() {
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		doThrow(new MailSendException("failed"))
			.when(javaMailSender)
			.send(any(MimeMessage.class));

		assertThatThrownBy(() -> emailService.sendEmail(new SendEmailRequest("test@email.com")))
			.isInstanceOf(EmailSendFailedException.class)
			.hasMessage("인증번호 이메일 발송에 실패했습니다.");

		verify(redisService).deleteValue(emailAuthCodeKey("test@email.com"));
	}

	@Test
	void validateAuthNumber_consumesAuthCodeWhenMatched() {
		when(redisService.getValue(emailAuthCodeKey("test@email.com"))).thenReturn("123456");

		emailService.validateAuthNumber(new ValidateAuthNumberRequest(" Test@Email.com ", 123456));

		verify(redisService).getValue(emailAuthCodeKey("test@email.com"));
		verify(redisService).setValue(emailVerifiedKey("test@email.com"), "true", VERIFIED_TTL);
		verify(redisService).deleteValue(emailAuthCodeKey("test@email.com"));
		verify(redisService).deleteValue(emailAuthFailCountKey("test@email.com"));
	}

	@Test
	void validateAuthNumber_throwsWhenAuthCodeIsInvalid() {
		when(redisService.getValue(emailAuthCodeKey("test@email.com"))).thenReturn("123456");

		assertThatThrownBy(() -> emailService.validateAuthNumber(
			new ValidateAuthNumberRequest("test@email.com", 654321)
		))
			.isInstanceOf(InvalidEmailAuthCodeException.class)
			.hasMessage("인증번호가 만료되었거나 올바르지 않습니다.");

		verify(redisService).incrementValue(emailAuthFailCountKey("test@email.com"));
		verify(redisService, never()).deleteValue(emailAuthCodeKey("test@email.com"));
	}

	@Test
	void validateAuthNumber_setsFailCountTtlWhenFirstAuthCodeFailureOccurs() {
		when(redisService.getValue(emailAuthCodeKey("test@email.com"))).thenReturn("123456");
		when(redisService.incrementValue(emailAuthFailCountKey("test@email.com"))).thenReturn(1L);

		assertThatThrownBy(() -> emailService.validateAuthNumber(
			new ValidateAuthNumberRequest("test@email.com", 654321)
		))
			.isInstanceOf(InvalidEmailAuthCodeException.class);

		verify(redisService).expire(emailAuthFailCountKey("test@email.com"), AUTH_CODE_TTL);
		verify(redisService, never()).deleteValue(emailAuthCodeKey("test@email.com"));
		verify(redisService, never()).deleteValue(emailAuthFailCountKey("test@email.com"));
	}

	@Test
	void validateAuthNumber_deletesAuthCodeWhenFailureCountReachesLimit() {
		when(redisService.getValue(emailAuthCodeKey("test@email.com"))).thenReturn("123456");
		when(redisService.incrementValue(emailAuthFailCountKey("test@email.com"))).thenReturn(5L);

		assertThatThrownBy(() -> emailService.validateAuthNumber(
			new ValidateAuthNumberRequest("test@email.com", 654321)
		))
			.isInstanceOf(InvalidEmailAuthCodeException.class);

		verify(redisService, never()).expire(emailAuthFailCountKey("test@email.com"), AUTH_CODE_TTL);
		verify(redisService).deleteValue(emailAuthCodeKey("test@email.com"));
		verify(redisService).deleteValue(emailAuthFailCountKey("test@email.com"));
	}

	@Test
	void validateAuthNumber_throwsWhenAuthCodeIsExpired() {
		when(redisService.getValue(emailAuthCodeKey("test@email.com"))).thenReturn(null);
		when(redisService.incrementValue(emailAuthFailCountKey("test@email.com"))).thenReturn(1L);

		assertThatThrownBy(() -> emailService.validateAuthNumber(
			new ValidateAuthNumberRequest("test@email.com", 123456)
		))
			.isInstanceOf(InvalidEmailAuthCodeException.class)
			.hasMessage("인증번호가 만료되었거나 올바르지 않습니다.");

		verify(redisService).incrementValue(emailAuthFailCountKey("test@email.com"));
		verify(redisService, never()).deleteValue(emailAuthCodeKey("test@email.com"));
	}

	@Test
	void consumeVerifiedSignUpEmail_deletesAndPassesWhenVerifiedFlagExists() {
		when(redisService.getAndDeleteValue(emailVerifiedKey("test@email.com"))).thenReturn("true");

		emailService.consumeVerifiedSignUpEmail(" Test@Email.com ");

		verify(redisService).getAndDeleteValue(emailVerifiedKey("test@email.com"));
	}

	@Test
	void consumeVerifiedSignUpEmail_throwsWhenVerifiedFlagDoesNotExist() {
		when(redisService.getAndDeleteValue(emailVerifiedKey("test@email.com"))).thenReturn(null);

		assertThatThrownBy(() -> emailService.consumeVerifiedSignUpEmail("test@email.com"))
			.isInstanceOf(EmailNotVerifiedException.class)
			.hasMessage("이메일 인증이 필요합니다.");
	}

	private String emailAuthCodeKey(String email) {
		return "email-auth-code:signup:" + hashEmail(email);
	}

	private String emailAuthFailCountKey(String email) {
		return "email-auth-fail-count:signup:" + hashEmail(email);
	}

	private String emailVerifiedKey(String email) {
		return "email-auth-verified:signup:" + hashEmail(email);
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
