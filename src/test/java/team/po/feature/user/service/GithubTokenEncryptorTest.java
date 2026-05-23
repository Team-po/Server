package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

class GithubTokenEncryptorTest {

	@Test
	void decrypt_returnsOriginalToken() {
		GithubTokenEncryptor encryptor = new GithubTokenEncryptor("test-token-encryption-secret");

		String ciphertext = encryptor.encrypt("github-access-token");

		assertThat(encryptor.decrypt(ciphertext)).isEqualTo("github-access-token");
	}

	@Test
	void decrypt_throwsWhenCiphertextIsInvalid() {
		GithubTokenEncryptor encryptor = new GithubTokenEncryptor("test-token-encryption-secret");

		assertThatThrownBy(() -> encryptor.decrypt("invalid-ciphertext"))
			.isInstanceOf(ApplicationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GITHUB_TOKEN_DECRYPTION_FAILED);
	}
}
