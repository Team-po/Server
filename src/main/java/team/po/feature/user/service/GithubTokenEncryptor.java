package team.po.feature.user.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

@Component
public class GithubTokenEncryptor {
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final String ALGORITHM = "AES";
	private static final int IV_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final SecureRandom secureRandom = new SecureRandom();
	private final String encryptionSecret;

	public GithubTokenEncryptor(@Value("${github.oauth.token-encryption-secret:}") String encryptionSecret) {
		this.encryptionSecret = encryptionSecret;
	}

	public String encrypt(String token) {
		if (!StringUtils.hasText(token)) {
			return null;
		}
		if (!StringUtils.hasText(encryptionSecret)) {
			throw new ApplicationException(ErrorCode.GITHUB_TOKEN_ENCRYPTION_FAILED, "GitHub 토큰 암호화 키가 설정되지 않았습니다.");
		}

		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(createKey(), ALGORITHM), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
					.put(iv)
					.put(ciphertext)
					.array());
		} catch (GeneralSecurityException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_TOKEN_ENCRYPTION_FAILED, "GitHub 토큰 암호화에 실패했습니다.", exception);
		}
	}

	private byte[] createKey() throws GeneralSecurityException {
		byte[] secretBytes = decodeSecret();
		return MessageDigest.getInstance("SHA-256").digest(secretBytes);
	}

	private byte[] decodeSecret() {
		try {
			return Base64.getDecoder().decode(encryptionSecret);
		} catch (IllegalArgumentException exception) {
			return encryptionSecret.getBytes(StandardCharsets.UTF_8);
		}
	}
}
