package team.po.feature.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import team.po.exception.CustomExceptionHandler;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.DeleteUserRequest;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.InvalidImageContentTypeException;
import team.po.feature.user.exception.InvalidPasswordException;
import team.po.feature.user.exception.InvalidProfileImageKeyException;
import team.po.feature.user.exception.InvalidTokenException;
import team.po.feature.user.exception.UserNotFoundException;
import team.po.feature.user.service.ImageService;
import team.po.feature.user.repository.UserRepository;
import team.po.feature.user.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private ImageService profileImagePresignService;

	@MockitoBean
	private UserRepository userRepository;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void signUp_returnsOk_whenRequestIsValid() throws Exception {
		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("test@email.com", "password123", "tester", 3, null)))
			.andExpect(status().isOk());

		verify(userService).signUp(new team.po.feature.user.dto.SignUpRequest("test@email.com", "password123", "tester", 3, null));
	}

	@Test
	void signUp_returnsBadRequestWithFieldErrors_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("invalid-email", "123", "", 0, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.email").value("이메일 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.fieldErrors.password").value("비밀번호는 8글자 이상이어야 합니다."))
			.andExpect(jsonPath("$.fieldErrors.nickname").value("닉네임 입력은 필수입니다."))
			.andExpect(jsonPath("$.fieldErrors.level").value("레벨은 1 이상이어야 합니다."));
	}

	@Test
	void signUp_returnsConflict_whenEmailAlreadyExists() throws Exception {
		doThrow(new DuplicatedEmailException(
			HttpStatus.CONFLICT,
			ErrorCodeConstants.EMAIL_ALREADY_EXISTS,
			"중복된 이메일이 존재합니다."
		)).when(userService).signUp(new team.po.feature.user.dto.SignUpRequest("test@email.com", "password123", "tester", 3, null));

		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("test@email.com", "password123", "tester", 3, null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.EMAIL_ALREADY_EXISTS))
			.andExpect(jsonPath("$.message").value("중복된 이메일이 존재합니다."));
	}

	@Test
	void signUp_acceptsProfileImageKey() throws Exception {
		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("test@email.com", "password123", "tester", 3, "images/sign-up/test.png")))
			.andExpect(status().isOk());

		verify(userService).signUp(new team.po.feature.user.dto.SignUpRequest(
			"test@email.com", "password123", "tester", 3, "images/sign-up/test.png"));
	}

	@Test
	void signUp_returnsBadRequest_whenProfileImageKeyIsTooLong() throws Exception {
		String longFileName = "a".repeat(250) + ".png";
		String profileImageKey = "images/sign-up/" + longFileName;

		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("test@email.com", "password123", "tester", 3, profileImageKey)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.profileImageKey").value("프로필 이미지 키는 255자 이하여야 합니다."));
	}

	@Test
	void signUp_returnsBadRequest_whenProfileImageKeyHasInvalidPattern() throws Exception {
		mockMvc.perform(post("/api/users/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpRequestJson("test@email.com", "password123", "tester", 3, "https://evil.com/image.png")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.profileImageKey").value("프로필 이미지 키 형식이 올바르지 않습니다."));
	}

	@Test
	void checkEmailDuplicate_returnsOk_whenEmailIsAvailable() throws Exception {
		doNothing().when(userService).checkEmailDuplication("test@email.com");

		mockMvc.perform(get("/api/users/check-email")
				.param("email", "test@email.com"))
			.andExpect(status().isOk());

		verify(userService).checkEmailDuplication("test@email.com");
	}

	@Test
	void checkEmailDuplicate_returnsConflict_whenEmailAlreadyExists() throws Exception {
		doThrow(new DuplicatedEmailException(
			HttpStatus.CONFLICT,
			ErrorCodeConstants.EMAIL_ALREADY_EXISTS,
			"중복된 이메일이 존재합니다."
		)).when(userService).checkEmailDuplication("test@email.com");

		mockMvc.perform(get("/api/users/check-email")
				.param("email", "test@email.com"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.EMAIL_ALREADY_EXISTS))
			.andExpect(jsonPath("$.message").value("중복된 이메일이 존재합니다."));
	}

	@Test
	void signIn_returnsOk_whenCredentialsAreValid() throws Exception {
		org.mockito.Mockito.when(userService.signIn(any()))
			.thenReturn(new SignInResponse("access-token", "refresh-token", Instant.parse("2026-03-16T11:00:00Z")));

		mockMvc.perform(post("/api/users/sign-in")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"test@email.com","password":"password123"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-token"))
			.andExpect(jsonPath("$.refreshToken").value("refresh-token"))
			.andExpect(jsonPath("$.expiresAt").value("2026-03-16T11:00:00Z"));
	}

	@Test
	void signIn_returnsUnauthorized_whenCredentialsAreInvalid() throws Exception {
		doThrow(new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."))
			.when(userService).signIn(any());

		mockMvc.perform(post("/api/users/sign-in")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"test@email.com","password":"wrong-password"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_CREDENTIALS))
			.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	void refreshToken_returnsOk_whenRefreshTokenIsValid() throws Exception {
		org.mockito.Mockito.when(userService.refreshToken(any()))
			.thenReturn(new RefreshTokenResponse("new-access-token", Instant.parse("2026-03-16T12:00:00Z")));

		mockMvc.perform(post("/api/users/refresh-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"refresh-token"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("new-access-token"))
			.andExpect(jsonPath("$.expiresAt").value("2026-03-16T12:00:00Z"));
	}

	@Test
	void refreshToken_returnsBadRequest_whenRefreshTokenIsBlank() throws Exception {
		mockMvc.perform(post("/api/users/refresh-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.refreshToken").exists());
	}

	@Test
	void refreshToken_returnsUnauthorized_whenRefreshTokenIsInvalid() throws Exception {
		doThrow(new InvalidTokenException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.INVALID_TOKEN,
			"유효하지 않은 리프레스 토큰입니다."
		)).when(userService).refreshToken(any());

		mockMvc.perform(post("/api/users/refresh-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"refreshToken":"invalid-refresh-token"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_TOKEN))
			.andExpect(jsonPath("$.message").value("유효하지 않은 리프레스 토큰입니다."));
	}

	@Test
	void getMyProfile_returnsOk_whenAuthenticatedUserExists() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		org.mockito.Mockito.when(userService.getMyProfile(authenticatedUser))
			.thenReturn(GetProfileResponse.builder()
				.email("test@email.com")
				.profileImage("profile.png")
				.description("hello")
				.nickname("tester")
				.temperature(50)
				.level(3)
				.build());

		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("test@email.com"))
			.andExpect(jsonPath("$.profileImage").value("profile.png"))
			.andExpect(jsonPath("$.description").value("hello"))
			.andExpect(jsonPath("$.nickname").value("tester"))
			.andExpect(jsonPath("$.temperature").value(50))
			.andExpect(jsonPath("$.level").value(3));
	}

	@Test
	void getMyProfile_returnsUnauthorized_whenAuthenticatedUserDoesNotExist() throws Exception {
		setMissingAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNEXISTED_USER))
			.andExpect(jsonPath("$.message").value("존재하지 않은 유저입니다."));

		verifyNoInteractions(userService);
	}

	@Test
	void createSignUpProfileImageUploadUrl_returnsOk_whenRequestIsValid() throws Exception {
		org.mockito.Mockito.when(profileImagePresignService.createSignUpUploadUrl(
			new team.po.feature.user.dto.ProfileImageUploadUrlRequest("image/webp")
		)).thenReturn(new ProfileImageUploadUrlResponse(
			"http://localhost:9000/team-po",
			Map.of(
				"key", "images/sign-up/test.webp",
				"Content-Type", "image/webp",
				"Policy", "encoded-policy"
			),
			"images/sign-up/test.webp",
			"image/webp",
			5_242_880L,
			Instant.parse("2026-04-07T12:00:00Z")
		));

		mockMvc.perform(post("/api/users/profile-image/upload-url")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"contentType":"image/webp"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadUrl").value("http://localhost:9000/team-po"))
			.andExpect(jsonPath("$.formFields.key").value("images/sign-up/test.webp"))
			.andExpect(jsonPath("$.formFields['Content-Type']").value("image/webp"))
			.andExpect(jsonPath("$.formFields.Policy").value("encoded-policy"))
			.andExpect(jsonPath("$.objectKey").value("images/sign-up/test.webp"))
			.andExpect(jsonPath("$.contentType").value("image/webp"))
			.andExpect(jsonPath("$.maxFileSizeBytes").value(5_242_880))
			.andExpect(jsonPath("$.expiresAt").value("2026-04-07T12:00:00Z"));
	}

	@Test
	void createSignUpProfileImageUploadUrl_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(post("/api/users/profile-image/upload-url")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"contentType":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.contentType").value("이미지 Content-Type은 필수입니다."));
	}

	@Test
	void createProfileImageUploadUrl_returnsOk_whenRequestIsValid() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		org.mockito.Mockito.when(profileImagePresignService.createProfileUploadUrl(
			authenticatedUser,
			new team.po.feature.user.dto.ProfileImageUploadUrlRequest("image/png")
		)).thenReturn(new ProfileImageUploadUrlResponse(
			"http://localhost:9000/team-po",
			Map.of(
				"key", "images/users/1/test.png",
				"Content-Type", "image/png",
				"Policy", "encoded-policy"
			),
			"images/users/1/test.png",
			"image/png",
			5_242_880L,
			Instant.parse("2026-04-07T12:00:00Z")
		));

		mockMvc.perform(post("/api/users/me/profile-image/upload-url")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"contentType":"image/png"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadUrl").value("http://localhost:9000/team-po"))
			.andExpect(jsonPath("$.formFields.key").value("images/users/1/test.png"))
			.andExpect(jsonPath("$.formFields['Content-Type']").value("image/png"))
			.andExpect(jsonPath("$.formFields.Policy").value("encoded-policy"))
			.andExpect(jsonPath("$.objectKey").value("images/users/1/test.png"))
			.andExpect(jsonPath("$.contentType").value("image/png"))
			.andExpect(jsonPath("$.maxFileSizeBytes").value(5_242_880))
			.andExpect(jsonPath("$.expiresAt").value("2026-04-07T12:00:00Z"));
	}

	@Test
	void createProfileImageUploadUrl_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(post("/api/users/me/profile-image/upload-url")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"contentType":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.contentType").value("이미지 Content-Type은 필수입니다."));
	}

	@Test
	void createProfileImageUploadUrl_returnsBadRequest_whenContentTypeIsUnsupported() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidImageContentTypeException(
			HttpStatus.BAD_REQUEST,
			ErrorCodeConstants.INVALID_IMAGE_CONTENT_TYPE,
			"지원하지 않는 이미지 형식입니다."
		)).when(profileImagePresignService).createProfileUploadUrl(
			authenticatedUser,
			new team.po.feature.user.dto.ProfileImageUploadUrlRequest("application/pdf")
		);

		mockMvc.perform(post("/api/users/me/profile-image/upload-url")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"contentType":"application/pdf"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_IMAGE_CONTENT_TYPE))
			.andExpect(jsonPath("$.message").value("지원하지 않는 이미지 형식입니다."));
	}

	@Test
	void editMyProfile_returnsBadRequest_whenProfileImageKeyWasNotIssued() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidProfileImageKeyException(
			HttpStatus.BAD_REQUEST,
			ErrorCodeConstants.INVALID_PROFILE_IMAGE_KEY,
			"발급되지 않았거나 만료된 프로필 이미지 키입니다."
		)).when(userService).editMyProfile(
			authenticatedUser,
			new EditProfileRequest("updated-description", "updated-nickname", 4, "images/users/1/test.png")
		);

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "updated-nickname", 4, "images/users/1/test.png")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_PROFILE_IMAGE_KEY))
			.andExpect(jsonPath("$.message").value("발급되지 않았거나 만료된 프로필 이미지 키입니다."));
	}

	@Test
	void editMyProfile_returnsOk_whenRequestIsValid() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "updated-nickname", 4, "images/users/1/test.png")))
			.andExpect(status().isOk());

		verify(userService).editMyProfile(authenticatedUser,
			new EditProfileRequest("updated-description", "updated-nickname", 4, "images/users/1/test.png"));
	}

	@Test
	void editMyProfile_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "", 0, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.nickname").value("닉네임 입력은 필수입니다."))
			.andExpect(jsonPath("$.fieldErrors.level").value("레벨은 1 이상이어야 합니다."));
	}

	@Test
	void editMyProfile_returnsBadRequest_whenProfileImageKeyIsTooLong() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");
		String longFileName = "a".repeat(250) + ".png";
		String profileImageKey = "images/users/1/" + longFileName;

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "updated-nickname", 4, profileImageKey)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.profileImageKey").value("프로필 이미지 키는 255자 이하여야 합니다."));
	}

	@Test
	void editMyProfile_returnsBadRequest_whenProfileImageKeyHasInvalidPattern() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "updated-nickname", 4, "../evil.png")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.profileImageKey").value("프로필 이미지 키 형식이 올바르지 않습니다."));
	}

	@Test
	void editMyProfile_returnsUnauthorized_whenAuthenticatedUserDoesNotExist() throws Exception {
		setMissingAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(editProfileRequestJson("updated-description", "updated-nickname", 4, null)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNEXISTED_USER))
			.andExpect(jsonPath("$.message").value("존재하지 않은 유저입니다."));

		verifyNoInteractions(userService);
	}

	@Test
	void editPassword_returnsOk_whenRequestIsValid() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me/password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"password123","afterPassword":"newPassword123"}
					"""))
			.andExpect(status().isOk());

		verify(userService).editPassword(
			authenticatedUser,
			new EditPasswordRequest("password123", "newPassword123")
		);
	}

	@Test
	void editPassword_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me/password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"123","afterPassword":"123"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.currentPassword").value("비밀번호는 8글자 이상이어야 합니다."))
			.andExpect(jsonPath("$.fieldErrors.afterPassword").value("비밀번호는 8글자 이상이어야 합니다."));
	}

	@Test
	void editPassword_returnsUnauthorized_whenPasswordDoesNotMatch() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidPasswordException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNMATCHED_PASSWORD,
			"현재 비밀번호와 동일하지 않습니다."
		)).when(userService).editPassword(
			authenticatedUser,
			new EditPasswordRequest("password123", "newPassword123")
		);

		mockMvc.perform(put("/api/users/me/password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"password123","afterPassword":"newPassword123"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNMATCHED_PASSWORD))
			.andExpect(jsonPath("$.message").value("현재 비밀번호와 동일하지 않습니다."));
	}

	@Test
	void deleteUser_returnsOk_whenRequestIsValid() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(delete("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"password123"}
					"""))
			.andExpect(status().isOk());

		verify(userService).deleteUser(
			authenticatedUser,
			new DeleteUserRequest("password123")
		);
	}

	@Test
	void deleteUser_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(delete("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"123"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.password").value("비밀번호는 8글자 이상이어야 합니다."));
	}

	@Test
	void deleteUser_returnsUnauthorized_whenPasswordDoesNotMatch() throws Exception {
		Users authenticatedUser = setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidPasswordException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNMATCHED_PASSWORD,
			"현재 비밀번호와 동일하지 않습니다."
		)).when(userService).deleteUser(
			authenticatedUser,
			new DeleteUserRequest("password123")
		);

		mockMvc.perform(delete("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"password123"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNMATCHED_PASSWORD))
			.andExpect(jsonPath("$.message").value("현재 비밀번호와 동일하지 않습니다."));
	}

	private String signUpRequestJson(String email, String password, String nickname, Integer level, String profileImageKey) {
		return """
			{"email":"%s","password":"%s","nickname":"%s","level":%s,"profileImageKey":%s}
			""".formatted(
			email,
			password,
			nickname,
			level == null ? "null" : level,
			profileImageKey == null ? "null" : "\"%s\"".formatted(profileImageKey)
		);
	}

	private String editProfileRequestJson(String description, String nickname, Integer level, String profileImageKey) {
		return """
			{"description":%s,"nickname":%s,"level":%s,"profileImageKey":%s}
			""".formatted(
			description == null ? "null" : "\"%s\"".formatted(description),
			nickname == null ? "null" : "\"%s\"".formatted(nickname),
			level == null ? "null" : level,
			profileImageKey == null ? "null" : "\"%s\"".formatted(profileImageKey)
		);
	}

	private Users setAuthenticatedUser(Long id, String email) {
		Users user = authenticatedUser(id, email);
		org.mockito.Mockito.when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(user));
		UserPrincipal principal = new UserPrincipal(id, email);
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return user;
	}

	private void setMissingAuthenticatedUser(Long id, String email) {
		org.mockito.Mockito.when(userRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());
		UserPrincipal principal = new UserPrincipal(id, email);
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
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
