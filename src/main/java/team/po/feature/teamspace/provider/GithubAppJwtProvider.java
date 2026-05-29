package team.po.feature.teamspace.provider;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import team.po.config.GithubAppProperties;

@Component
public class GithubAppJwtProvider {
	private static final Duration CLOCK_SKEW_ALLOWANCE = Duration.ofSeconds(60);
	private static final Duration TOKEN_TTL = Duration.ofMinutes(9);
	private static final String PKCS8_PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
	private static final String PKCS8_PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";
	private static final String PKCS1_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
	private static final String PKCS1_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";

	private final GithubAppProperties githubAppProperties;
	private final Clock clock;

	@Autowired
	public GithubAppJwtProvider(GithubAppProperties githubAppProperties) {
		this(githubAppProperties, Clock.systemUTC());
	}

	GithubAppJwtProvider(GithubAppProperties githubAppProperties, Clock clock) {
		this.githubAppProperties = githubAppProperties;
		this.clock = clock;
	}

	public String generateJwt() {
		Instant now = Instant.now(clock);
		Instant issuedAt = now.minus(CLOCK_SKEW_ALLOWANCE);
		Instant expiresAt = now.plus(TOKEN_TTL);

		return Jwts.builder()
			.issuer(String.valueOf(githubAppProperties.id()))
			.issuedAt(Date.from(issuedAt))
			.expiration(Date.from(expiresAt))
			.signWith(parsePrivateKey(githubAppProperties.privateKey()), Jwts.SIG.RS256)
			.compact();
	}

	private PrivateKey parsePrivateKey(String privateKey) {
		try {
			String normalizedPrivateKey = privateKey.replace("\\n", "\n").trim();
			if (normalizedPrivateKey.contains(PKCS1_RSA_PRIVATE_KEY_HEADER)) {
				return parsePkcs1PrivateKey(normalizedPrivateKey);
			}
			return parsePkcs8PrivateKey(normalizedPrivateKey);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Github App private key is invalid.", exception);
		}
	}

	private PrivateKey parsePkcs8PrivateKey(String privateKey) throws Exception {
		byte[] keyBytes = decodePem(privateKey, PKCS8_PRIVATE_KEY_HEADER, PKCS8_PRIVATE_KEY_FOOTER);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
		return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
	}

	private PrivateKey parsePkcs1PrivateKey(String privateKey) throws Exception {
		byte[] keyBytes = decodePem(privateKey, PKCS1_RSA_PRIVATE_KEY_HEADER, PKCS1_RSA_PRIVATE_KEY_FOOTER);
		DerReader derReader = new DerReader(keyBytes);
		derReader.readSequence();
		derReader.readInteger();
		BigInteger modulus = derReader.readInteger();
		BigInteger publicExponent = derReader.readInteger();
		BigInteger privateExponent = derReader.readInteger();
		BigInteger primeP = derReader.readInteger();
		BigInteger primeQ = derReader.readInteger();
		BigInteger primeExponentP = derReader.readInteger();
		BigInteger primeExponentQ = derReader.readInteger();
		BigInteger crtCoefficient = derReader.readInteger();

		RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
			modulus,
			publicExponent,
			privateExponent,
			primeP,
			primeQ,
			primeExponentP,
			primeExponentQ,
			crtCoefficient
		);
		return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
	}

	private byte[] decodePem(String privateKey, String header, String footer) {
		String base64Key = privateKey
			.replace(header, "")
			.replace(footer, "")
			.replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64Key);
	}

	private static class DerReader {
		private final byte[] bytes;
		private int index;

		private DerReader(byte[] bytes) {
			this.bytes = bytes;
		}

		private void readSequence() {
			readTag(0x30);
			readLength();
		}

		private BigInteger readInteger() {
			readTag(0x02);
			int length = readLength();
			byte[] value = new byte[length];
			System.arraycopy(bytes, index, value, 0, length);
			index += length;
			return new BigInteger(value);
		}

		private void readTag(int expectedTag) {
			int actualTag = bytes[index++] & 0xff;
			if (actualTag != expectedTag) {
				throw new IllegalArgumentException("Invalid DER tag.");
			}
		}

		private int readLength() {
			int firstByte = bytes[index++] & 0xff;
			if (firstByte < 0x80) {
				return firstByte;
			}

			int byteCount = firstByte & 0x7f;
			int length = 0;
			for (int i = 0; i < byteCount; i++) {
				length = (length << 8) + (bytes[index++] & 0xff);
			}
			return length;
		}
	}
}
