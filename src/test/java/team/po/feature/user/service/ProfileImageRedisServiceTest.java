package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.common.redis.RedisService;
import team.po.feature.user.exception.InvalidProfileImageKeyException;

@ExtendWith(MockitoExtension.class)
class ProfileImageRedisServiceTest {

	@Mock
	private RedisService redisService;

	private ProfileImageRedisService profileImageRedisService;

	@BeforeEach
	void setUp() {
		profileImageRedisService = new ProfileImageRedisService(redisService);
		ReflectionTestUtils.setField(profileImageRedisService, "ticketTtl", Duration.ofMinutes(10));
	}

	@Test
	void createSignUpTicket_storesObjectKeyWithTtl() {
		profileImageRedisService.createSignUpTicket("images/sign-up/test.png", "image/png");

		verify(redisService).setValue(
			"profile-image-upload:SIGN_UP:images/sign-up/test.png",
			"image/png",
			Duration.ofMinutes(10)
		);
	}

	@Test
	void createProfileUpdateTicket_storesObjectKeyWithUserScopeAndTtl() {
		profileImageRedisService.createProfileUpdateTicket(1L, "images/users/1/test.png", "image/png");

		verify(redisService).setValue(
			"profile-image-upload:PROFILE_UPDATE:1:images/users/1/test.png",
			"image/png",
			Duration.ofMinutes(10)
		);
	}

	@Test
	void consumeSignUpTicket_deletesAndPassesWhenTicketExists() {
		when(redisService.getAndDeleteValue("profile-image-upload:SIGN_UP:images/sign-up/test.png"))
			.thenReturn("image/png");

		profileImageRedisService.consumeSignUpTicket("images/sign-up/test.png");

		verify(redisService).getAndDeleteValue("profile-image-upload:SIGN_UP:images/sign-up/test.png");
	}

	@Test
	void consumeProfileUpdateTicket_deletesAndPassesWhenTicketExists() {
		when(redisService.getAndDeleteValue("profile-image-upload:PROFILE_UPDATE:1:images/users/1/test.png"))
			.thenReturn("image/png");

		profileImageRedisService.consumeProfileUpdateTicket(1L, "images/users/1/test.png");

		verify(redisService).getAndDeleteValue("profile-image-upload:PROFILE_UPDATE:1:images/users/1/test.png");
	}

	@Test
	void consumeProfileUpdateTicket_throwsWhenTicketDoesNotExist() {
		when(redisService.getAndDeleteValue("profile-image-upload:PROFILE_UPDATE:1:images/users/1/test.png"))
			.thenReturn(null);

		assertThatThrownBy(() -> profileImageRedisService.consumeProfileUpdateTicket(1L, "images/users/1/test.png"))
			.isInstanceOf(InvalidProfileImageKeyException.class)
			.hasMessage("발급되지 않았거나 만료된 프로필 이미지 키입니다.");
	}
}
