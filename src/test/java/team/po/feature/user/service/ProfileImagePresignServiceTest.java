package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.ProfileImageUploadUrlRequest;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.exception.InvalidImageContentTypeException;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class ProfileImagePresignServiceTest {

	@Mock
	private S3Presigner s3Presigner;

	@Mock
	private PresignedPutObjectRequest presignedPutObjectRequest;

	private ImageService profileImagePresignService;

	@BeforeEach
	void setUp() {
		profileImagePresignService = new ImageService(s3Presigner);
		ReflectionTestUtils.setField(profileImagePresignService, "bucket", "team-po");
		ReflectionTestUtils.setField(profileImagePresignService, "dir", "images");
		ReflectionTestUtils.setField(profileImagePresignService, "presignedExpiration", java.time.Duration.ofMinutes(5));
	}

	@Test
	void createProfileUploadUrl_returnsPresignedUrlForSupportedImageType() throws Exception {
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);
		when(presignedPutObjectRequest.url()).thenReturn(new URL("http://localhost:9000/team-po/images/users/1/test.png"));

		ProfileImageUploadUrlResponse response = profileImagePresignService.createProfileUploadUrl(
			authenticatedUser(1L, "test@email.com"),
			new ProfileImageUploadUrlRequest("image/png")
		);

		assertThat(response.uploadUrl()).isEqualTo("http://localhost:9000/team-po/images/users/1/test.png");
		assertThat(response.contentType()).isEqualTo("image/png");
		assertThat(response.objectKey()).startsWith("images/users/1/");
		assertThat(response.objectKey()).endsWith(".png");
		assertThat(response.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(60));

		ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
		verify(s3Presigner).presignPutObject(captor.capture());
		PutObjectRequest putObjectRequest = captor.getValue().putObjectRequest();
		assertThat(putObjectRequest.bucket()).isEqualTo("team-po");
		assertThat(putObjectRequest.key()).isEqualTo(response.objectKey());
		assertThat(putObjectRequest.contentType()).isEqualTo("image/png");
	}

	@Test
	void createProfileUploadUrl_normalizesContentTypeBeforeSigning() throws Exception {
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);
		when(presignedPutObjectRequest.url()).thenReturn(new URL("http://localhost:9000/team-po/images/users/1/test.jpg"));

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
	void createSignUpUploadUrl_createsSignUpScopedObjectKey() throws Exception {
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);
		when(presignedPutObjectRequest.url()).thenReturn(new URL("http://localhost:9000/team-po/images/sign-up/test.webp"));

		ProfileImageUploadUrlResponse response = profileImagePresignService.createSignUpUploadUrl(
			new ProfileImageUploadUrlRequest("image/webp")
		);

		assertThat(response.objectKey()).startsWith("images/sign-up/");
		assertThat(response.objectKey()).endsWith(".webp");
		assertThat(response.contentType()).isEqualTo("image/webp");
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
