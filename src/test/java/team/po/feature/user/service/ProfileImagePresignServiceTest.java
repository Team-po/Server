package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.ProfileImageUploadUrlRequest;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.exception.InvalidImageContentTypeException;

@ExtendWith(MockitoExtension.class)
class ProfileImagePresignServiceTest {

	@Mock
	private ProfileImageRedisService profileImageRedisService;

	private ImageService profileImagePresignService;

	@BeforeEach
	void setUp() {
		profileImagePresignService = new ImageService(profileImageRedisService);
		ReflectionTestUtils.setField(profileImagePresignService, "accessKey", "test-access-key");
		ReflectionTestUtils.setField(profileImagePresignService, "secretKey", "test-secret-key");
		ReflectionTestUtils.setField(profileImagePresignService, "region", "ap-northeast-2");
		ReflectionTestUtils.setField(profileImagePresignService, "endpoint", "http://localhost:9000");
		ReflectionTestUtils.setField(profileImagePresignService, "bucket", "team-po");
		ReflectionTestUtils.setField(profileImagePresignService, "dir", "images");
		ReflectionTestUtils.setField(profileImagePresignService, "presignedExpiration", java.time.Duration.ofMinutes(5));
		ReflectionTestUtils.setField(profileImagePresignService, "maxUploadSizeBytes", 5_242_880L);
	}

	@Test
	void createProfileUploadUrl_returnsPresignedPostForSupportedImageType() {
		ProfileImageUploadUrlResponse response = profileImagePresignService.createProfileUploadUrl(
			authenticatedUser(1L, "test@email.com"),
			new ProfileImageUploadUrlRequest("image/png")
		);

		assertThat(response.uploadUrl()).isEqualTo("http://localhost:9000/team-po");
		assertThat(response.contentType()).isEqualTo("image/png");
		assertThat(response.objectKey()).startsWith("images/users/1/");
		assertThat(response.objectKey()).endsWith(".png");
		assertThat(response.maxFileSizeBytes()).isEqualTo(5_242_880L);
		assertThat(response.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(60));
		assertThat(response.formFields())
			.containsEntry("key", response.objectKey())
			.containsEntry("Content-Type", "image/png")
			.containsEntry("X-Amz-Algorithm", "AWS4-HMAC-SHA256")
			.containsKey("X-Amz-Signature");
		assertThat(decodedPolicy(response))
			.contains("\"bucket\":\"team-po\"")
			.contains("\"key\":\"" + response.objectKey() + "\"")
			.contains("[\"content-length-range\",1,5242880]");
		verify(profileImageRedisService).createProfileUpdateTicket(1L, response.objectKey(), "image/png");
	}

	@Test
	void createProfileUploadUrl_normalizesContentTypeBeforeSigning() {
		ProfileImageUploadUrlResponse response = profileImagePresignService.createProfileUploadUrl(
			authenticatedUser(1L, "test@email.com"),
			new ProfileImageUploadUrlRequest("IMAGE/JPEG; charset=UTF-8")
		);

		assertThat(response.contentType()).isEqualTo("image/jpeg");
		assertThat(response.objectKey()).endsWith(".jpg");
	}

	@Test
	void createProfileUploadUrl_throwsWhenContentTypeIsUnsupported() {
		assertThatThrownBy(() -> profileImagePresignService.createProfileUploadUrl(
			authenticatedUser(1L, "test@email.com"),
			new ProfileImageUploadUrlRequest("application/pdf")
		))
			.isInstanceOf(InvalidImageContentTypeException.class)
			.hasMessage("지원하지 않는 이미지 형식입니다.");
	}

	@Test
	void createSignUpUploadUrl_createsSignUpScopedObjectKey() {
		ProfileImageUploadUrlResponse response = profileImagePresignService.createSignUpUploadUrl(
			new ProfileImageUploadUrlRequest("image/webp")
		);

		assertThat(response.objectKey()).startsWith("images/sign-up/");
		assertThat(response.objectKey()).endsWith(".webp");
		assertThat(response.contentType()).isEqualTo("image/webp");
		verify(profileImageRedisService).createSignUpTicket(response.objectKey(), "image/webp");
	}

	private String decodedPolicy(ProfileImageUploadUrlResponse response) {
		byte[] decodedPolicy = Base64.getDecoder().decode(response.formFields().get("Policy"));
		return new String(decodedPolicy, StandardCharsets.UTF_8);
	}

	private Users authenticatedUser(Long id, String email) {
		Users user = Users.builder()
			.email(email)
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
