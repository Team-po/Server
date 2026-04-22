package team.po.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.mail")
public record EmailAuthProperties(
	String username,
	Duration authCodeTtl,
	Duration verifiedTtl,
	String authCodeSubject
) {
	private static final Duration DEFAULT_AUTH_CODE_TTL = Duration.ofMinutes(5);
	private static final Duration DEFAULT_VERIFIED_TTL = Duration.ofMinutes(10);
	private static final String DEFAULT_AUTH_CODE_SUBJECT = "TeamPo 이메일 인증번호";

	public EmailAuthProperties {
		if (username == null) {
			username = "";
		}
		if (authCodeTtl == null) {
			authCodeTtl = DEFAULT_AUTH_CODE_TTL;
		}
		if (verifiedTtl == null) {
			verifiedTtl = DEFAULT_VERIFIED_TTL;
		}
		if (authCodeSubject == null) {
			authCodeSubject = DEFAULT_AUTH_CODE_SUBJECT;
		}
	}
}
