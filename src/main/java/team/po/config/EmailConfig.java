package team.po.config;

import java.util.Locale;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({MailProperties.class, EmailAuthProperties.class})
public class EmailConfig {

	private static final String DEFAULT_PROTOCOL = "smtp";
	private static final int DEFAULT_TLS_PORT = 587;

	private final Environment environment;

	public EmailConfig(Environment environment) {
		this.environment = environment;
	}

	@Bean
	@ConditionalOnMissingBean(JavaMailSender.class)
	public JavaMailSender javaMailSender(MailProperties mailProperties) {
		JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
		String protocol = createMailProtocol(mailProperties);

		javaMailSender.setHost(mailProperties.getHost());
		if (mailProperties.getPort() != null) {
			javaMailSender.setPort(mailProperties.getPort());
		} else if (DEFAULT_PROTOCOL.equals(protocol)) {
			javaMailSender.setPort(DEFAULT_TLS_PORT);
		}
		javaMailSender.setUsername(mailProperties.getUsername());
		javaMailSender.setPassword(mailProperties.getPassword());
		javaMailSender.setProtocol(protocol);
		if (mailProperties.getDefaultEncoding() != null) {
			javaMailSender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
		}

		Properties javaMailProperties = javaMailProperties(mailProperties, protocol);
		if (!javaMailProperties.isEmpty()) {
			javaMailSender.setJavaMailProperties(javaMailProperties);
		}

		return javaMailSender;
	}

	private Properties javaMailProperties(MailProperties mailProperties, String protocol) {
		Properties properties = new Properties();
		properties.putAll(mailProperties.getProperties());

		if (DEFAULT_PROTOCOL.equals(protocol)) {
			properties.putIfAbsent("mail.smtp.starttls.enable", "true");
			properties.putIfAbsent("mail.smtp.starttls.required", "true");
			properties.putIfAbsent("mail.smtp.ssl.enable", "false");
			properties.putIfAbsent("mail.smtp.ssl.checkserveridentity", "true");
			properties.putIfAbsent("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
		}

		if (StringUtils.hasText(mailProperties.getUsername())) {
			properties.putIfAbsent("mail.%s.auth".formatted(protocol), "true");
		}

		validateSecureSmtpOverrides(mailProperties, protocol, properties);

		return properties;
	}

	private void validateSecureSmtpOverrides(
		MailProperties mailProperties,
		String protocol,
		Properties properties
	) {
		if (!DEFAULT_PROTOCOL.equals(protocol) || acceptsInsecureSmtpOverrides()) {
			return;
		}

		rejectFalseSmtpProperty(properties, "mail.smtp.starttls.enable");
		rejectFalseSmtpProperty(properties, "mail.smtp.starttls.required");
		rejectFalseSmtpProperty(properties, "mail.smtp.ssl.checkserveridentity");
		if (StringUtils.hasText(mailProperties.getUsername())) {
			rejectFalseSmtpProperty(properties, "mail.smtp.auth");
		}
	}

	private boolean acceptsInsecureSmtpOverrides() {
		return environment.acceptsProfiles(Profiles.of("local", "test"));
	}

	private void rejectFalseSmtpProperty(Properties properties, String propertyName) {
		String propertyValue = properties.getProperty(propertyName);
		if ("false".equalsIgnoreCase(propertyValue)) {
			throw new IllegalStateException(
				"%s cannot be false outside local/test profiles.".formatted(propertyName)
			);
		}
	}

	private String createMailProtocol(MailProperties mailProperties) {
		if (StringUtils.hasText(mailProperties.getProtocol())) {
			return mailProperties.getProtocol().toLowerCase(Locale.ROOT);
		}

		return DEFAULT_PROTOCOL;
	}
}
