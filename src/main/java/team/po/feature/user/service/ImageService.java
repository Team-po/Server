package team.po.feature.user.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.ProfileImageUploadUrlRequest;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.exception.InvalidImageContentTypeException;

@Service
@RequiredArgsConstructor
public class ImageService {
	private static final String S3_SERVICE = "s3";
	private static final String AWS4_REQUEST = "aws4_request";
	private static final String SIGNATURE_ALGORITHM = "AWS4-HMAC-SHA256";
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final DateTimeFormatter DATE_STAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
		.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter AMZ_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
		.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter POLICY_EXPIRATION_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
		.withZone(ZoneOffset.UTC);
	private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
		"image/jpeg", "jpg",
		"image/png", "png",
		"image/gif", "gif",
		"image/webp", "webp"
	);

	private final ProfileImageRedisService profileImageRedisService;

	@Value("${cloud.aws.credentials.access-key}")
	private String accessKey;

	@Value("${cloud.aws.credentials.secret-key}")
	private String secretKey;

	@Value("${cloud.aws.region.static}")
	private String region;

	@Value("${cloud.aws.s3.endpoint:}")
	private String endpoint;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Value("${cloud.aws.s3.dir}")
	private String dir;

	@Value("${cloud.aws.s3.presigned-expiration:PT5M}")
	private Duration presignedExpiration;

	@Value("${cloud.aws.s3.max-upload-size-bytes:5242880}")
	private long maxUploadSizeBytes;

	public ProfileImageUploadUrlResponse createSignUpUploadUrl(ProfileImageUploadUrlRequest request) {
		String contentType = normalizeAndValidateContentType(request.contentType());
		String objectKey = buildSignUpObjectKey(extensionOf(contentType));
		ProfileImageUploadUrlResponse response = createUploadUrl(objectKey, contentType);
		profileImageRedisService.createSignUpTicket(objectKey, contentType);
		return response;
	}

	public ProfileImageUploadUrlResponse createProfileUploadUrl(Users loginUser, ProfileImageUploadUrlRequest request) {
		String contentType = normalizeAndValidateContentType(request.contentType());
		String objectKey = buildProfileObjectKey(loginUser.getId(), extensionOf(contentType));
		ProfileImageUploadUrlResponse response = createUploadUrl(objectKey, contentType);
		profileImageRedisService.createProfileUpdateTicket(loginUser.getId(), objectKey, contentType);
		return response;
	}

	private ProfileImageUploadUrlResponse createUploadUrl(String objectKey, String contentType) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(presignedExpiration);
		String dateStamp = DATE_STAMP_FORMATTER.format(now);
		String amzDate = AMZ_DATE_FORMATTER.format(now);
		String credential = accessKey + "/" + credentialScope(dateStamp);
		String policy = createPostPolicy(expiresAt, objectKey, contentType, amzDate, credential);
		String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
		String signature = signature(encodedPolicy, dateStamp);

		Map<String, String> formFields = new LinkedHashMap<>();
		formFields.put("key", objectKey);
		formFields.put("Content-Type", contentType);
		formFields.put("X-Amz-Algorithm", SIGNATURE_ALGORITHM);
		formFields.put("X-Amz-Credential", credential);
		formFields.put("X-Amz-Date", amzDate);
		formFields.put("Policy", encodedPolicy);
		formFields.put("X-Amz-Signature", signature);

		return new ProfileImageUploadUrlResponse(
			uploadActionUrl(),
			formFields,
			objectKey,
			contentType,
			maxUploadSizeBytes,
			expiresAt
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

	private String uploadActionUrl() {
		if (endpoint == null || endpoint.isBlank()) {
			return "https://s3." + region + ".amazonaws.com/" + bucket;
		}

		String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
		return normalizedEndpoint + "/" + bucket;
	}

	private String credentialScope(String dateStamp) {
		return dateStamp + "/" + region + "/" + S3_SERVICE + "/" + AWS4_REQUEST;
	}

	private String createPostPolicy(
		Instant expiresAt,
		String objectKey,
		String contentType,
		String amzDate,
		String credential
	) {
		return """
			{"expiration":"%s","conditions":[{"bucket":"%s"},{"key":"%s"},{"Content-Type":"%s"},["content-length-range",1,%d],{"x-amz-algorithm":"%s"},{"x-amz-credential":"%s"},{"x-amz-date":"%s"}]}
			""".formatted(
			POLICY_EXPIRATION_FORMATTER.format(expiresAt.truncatedTo(ChronoUnit.MILLIS)),
			bucket,
			objectKey,
			contentType,
			maxUploadSizeBytes,
			SIGNATURE_ALGORITHM,
			credential,
			amzDate
		).strip();
	}

	private String signature(String encodedPolicy, String dateStamp) {
		byte[] signingKey = signingKey(dateStamp);
		return HexFormat.of().formatHex(hmac(signingKey, encodedPolicy));
	}

	private byte[] signingKey(String dateStamp) {
		byte[] dateKey = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
		byte[] regionKey = hmac(dateKey, region);
		byte[] serviceKey = hmac(regionKey, S3_SERVICE);
		return hmac(serviceKey, AWS4_REQUEST);
	}

	private byte[] hmac(byte[] key, String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(key, HMAC_SHA256));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("S3 POST policy signing failed.", exception);
		}
	}
}
