package team.po.feature.teamspace.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import team.po.config.GithubAppProperties;

class GithubAppJwtProviderTest {
	private static final Instant FIXED_NOW = Instant.parse("2030-05-20T00:00:00Z");

	@Test
	void generateJwt_createsRs256TokenWithGithubAppClaims() throws Exception {
		KeyPair keyPair = generateRsaKeyPair();
		GithubAppJwtProvider provider = new GithubAppJwtProvider(
			properties(toPkcs8Pem(keyPair)),
			Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
		);

		String token = provider.generateJwt();

		Claims claims = Jwts.parser()
			.verifyWith((RSAPublicKey)keyPair.getPublic())
			.build()
			.parseSignedClaims(token)
			.getPayload();

		assertThat(claims.getIssuer()).isEqualTo("12345");
		assertThat(claims.getIssuedAt()).isEqualTo(Date.from(FIXED_NOW.minusSeconds(60)));
		assertThat(claims.getExpiration()).isEqualTo(Date.from(FIXED_NOW.plus(Duration.ofMinutes(9))));
	}

	@Test
	void generateJwt_acceptsEscapedNewlinePrivateKey() throws Exception {
		KeyPair keyPair = generateRsaKeyPair();
		String escapedPrivateKey = toPkcs8Pem(keyPair).replace("\n", "\\n");
		GithubAppJwtProvider provider = new GithubAppJwtProvider(
			properties(escapedPrivateKey),
			Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
		);

		String token = provider.generateJwt();

		Claims claims = Jwts.parser()
			.verifyWith((RSAPublicKey)keyPair.getPublic())
			.build()
			.parseSignedClaims(token)
			.getPayload();
		assertThat(claims.getIssuer()).isEqualTo("12345");
	}

	@Test
	void generateJwt_throwsIllegalArgumentException_whenPrivateKeyIsInvalid() {
		GithubAppJwtProvider provider = new GithubAppJwtProvider(
			properties("invalid-private-key"),
			Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
		);

		assertThatThrownBy(provider::generateJwt)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("GitHub App private key is invalid.");
	}

	private KeyPair generateRsaKeyPair() throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		return keyPairGenerator.generateKeyPair();
	}

	private GithubAppProperties properties(String privateKey) {
		return new GithubAppProperties(12345L, "teampo-dev", privateKey, Duration.ofMinutes(5), "https://api.github.com");
	}

	private String toPkcs8Pem(KeyPair keyPair) {
		String base64Key = Base64.getMimeEncoder(64, "\n".getBytes())
			.encodeToString(keyPair.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n"
			+ base64Key
			+ "\n-----END PRIVATE KEY-----";
	}
}
