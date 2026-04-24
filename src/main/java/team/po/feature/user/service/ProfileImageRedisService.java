package team.po.feature.user.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.common.redis.RedisService;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ProfileImageRedisService {
	private static final String KEY_PREFIX = "profile-image-upload";
	private static final String SIGN_UP_PURPOSE = "SIGN_UP";
	private static final String PROFILE_UPDATE_PURPOSE = "PROFILE_UPDATE";

	private final RedisService redisService;

	@Value("${cloud.aws.s3.profile-image-upload-ticket-ttl:PT10M}")
	private Duration ticketTtl;

	public void createSignUpTicket(String objectKey, String contentType) {
		redisService.setValue(signUpKey(objectKey), contentType, ticketTtl);
	}

	public void createProfileUpdateTicket(Long userId, String objectKey, String contentType) {
		redisService.setValue(profileUpdateKey(userId, objectKey), contentType, ticketTtl);
	}

	public void consumeSignUpTicket(String objectKey) {
		consumeTicket(signUpKey(objectKey));
	}

	public void consumeProfileUpdateTicket(Long userId, String objectKey) {
		consumeTicket(profileUpdateKey(userId, objectKey));
	}

	private void consumeTicket(String redisKey) {
		Object ticket = redisService.getAndDeleteValue(redisKey);
		if (ticket == null) {
			throw new ApplicationException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
		}
	}

	private String signUpKey(String objectKey) {
		return KEY_PREFIX + ":" + SIGN_UP_PURPOSE + ":" + objectKey;
	}

	private String profileUpdateKey(Long userId, String objectKey) {
		return KEY_PREFIX + ":" + PROFILE_UPDATE_PURPOSE + ":" + userId + ":" + objectKey;
	}
}
