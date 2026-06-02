package team.po.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailConfigTest {

	@Test
	void javaMailSender_enablesTlsDefaultsForSmtp() {
		MailProperties mailProperties = new MailProperties();
		mailProperties.setHost("smtp.example.com");
		mailProperties.setProtocol("smtp");

		JavaMailSenderImpl javaMailSender = (JavaMailSenderImpl)emailConfig().javaMailSender(mailProperties);

		assertThat(javaMailSender.getJavaMailProperties())
			.containsEntry("mail.smtp.starttls.enable", "true")
			.containsEntry("mail.smtp.starttls.required", "true")
			.containsEntry("mail.smtp.ssl.enable", "false");
	}

	@Test
	void javaMailSender_respectsExplicitSmtpOverridesForLocalProfile() {
		MailProperties mailProperties = new MailProperties();
		mailProperties.setHost("localhost");
		mailProperties.setProtocol("smtp");
		mailProperties.setUsername("no-reply@teampo.local");
		mailProperties.getProperties().put("mail.smtp.auth", "false");
		mailProperties.getProperties().put("mail.smtp.starttls.enable", "false");
		mailProperties.getProperties().put("mail.smtp.starttls.required", "false");

		JavaMailSenderImpl javaMailSender = (JavaMailSenderImpl)emailConfig("local").javaMailSender(mailProperties);

		assertThat(javaMailSender.getJavaMailProperties())
			.containsEntry("mail.smtp.auth", "false")
			.containsEntry("mail.smtp.starttls.enable", "false")
			.containsEntry("mail.smtp.starttls.required", "false");
	}

	@Test
	void javaMailSender_rejectsInsecureSmtpOverridesOutsideLocalProfiles() {
		MailProperties mailProperties = new MailProperties();
		mailProperties.setHost("smtp.example.com");
		mailProperties.setProtocol("smtp");
		mailProperties.setUsername("no-reply@team-po.cloud");
		mailProperties.getProperties().put("mail.smtp.starttls.enable", "false");

		assertThatThrownBy(() -> emailConfig().javaMailSender(mailProperties))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("mail.smtp.starttls.enable cannot be false outside local/test profiles.");
	}

	private EmailConfig emailConfig(String... activeProfiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfiles);
		return new EmailConfig(environment);
	}
}
