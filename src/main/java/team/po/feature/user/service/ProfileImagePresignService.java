package team.po.feature.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUserInfo;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.dto.ProfileImageUploadUrlRequest;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.exception.InvalidImageContentTypeException;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ProfileImagePresignService {
	private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
		"image/jpeg", "jpg",
		"image/png", "png",
		"image/gif", "gif",
		"image/webp", "webp"
	);

	private final S3Presigner s3Presigner;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Value("${cloud.aws.s3.dir}")
	private String dir;

	@Value("${cloud.aws.s3.presigned-expiration:PT5M}")
	private Duration presignedExpiration;

	public ProfileImageUploadUrlResponse createSignUpUploadUrl(ProfileImageUploadUrlRequest request) {
		String contentType = normalizeAndValidateContentType(request.contentType());
		String objectKey = buildSignUpObjectKey(extensionOf(contentType));
		return createUploadUrl(objectKey, contentType);
	}

	public ProfileImageUploadUrlResponse createProfileUploadUrl(LoginUserInfo loginUser, ProfileImageUploadUrlRequest request) {
		String contentType = normalizeAndValidateContentType(request.contentType());
		String objectKey = buildProfileObjectKey(loginUser.id(), extensionOf(contentType));
		return createUploadUrl(objectKey, contentType);
	}

	private ProfileImageUploadUrlResponse createUploadUrl(String objectKey, String contentType) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(bucket)
			.key(objectKey)
			.contentType(contentType)
			.build();

		PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(presignedExpiration)
			.putObjectRequest(putObjectRequest)
			.build();

		PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(putObjectPresignRequest);

		return new ProfileImageUploadUrlResponse(
			presignedRequest.url().toString(),
			objectKey,
			contentType,
			Instant.now().plus(presignedExpiration)
		);
	}

	private String normalizeAndValidateContentType(String contentType) {
		String normalizedContentType = contentType.trim()
			.toLowerCase(Locale.ROOT)
			.split(";", 2)[0];

		if (!CONTENT_TYPE_TO_EXTENSION.containsKey(normalizedContentType)) {
			throw new InvalidImageContentTypeException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.INVALID_IMAGE_CONTENT_TYPE,
				"지원하지 않는 이미지 형식입니다."
			);
		}

		return normalizedContentType;
	}

	private String extensionOf(String contentType) {
		return CONTENT_TYPE_TO_EXTENSION.get(contentType);
	}

	private String buildSignUpObjectKey(String extension) {
		String normalizedDir = normalizedDir();
		return normalizedDir + "/sign-up/" + UUID.randomUUID() + "." + extension;
	}

	private String buildProfileObjectKey(Long userId, String extension) {
		String normalizedDir = normalizedDir();
		return normalizedDir + "/users/" + userId + "/" + UUID.randomUUID() + "." + extension;
	}

	private String normalizedDir() {
		return dir.replaceAll("^/+", "").replaceAll("/+$", "");
	}
}
