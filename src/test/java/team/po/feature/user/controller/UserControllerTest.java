package team.po.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import team.po.common.auth.LoginUserInfo;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.CustomUserExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.dto.DeleteUserRequest;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.InvalidPasswordException;
import team.po.feature.user.exception.InvalidTokenException;
import team.po.feature.user.exception.UserNotFoundException;
import team.po.feature.user.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomUserExceptionHandler.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void signUp_returnsOk_whenRequestIsValid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("test@email.com", "password123", "tester", 3))
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).signUp(any(), isNull());
	}

	@Test
	void signUp_returnsBadRequestWithFieldErrors_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("invalid-email", "123", "", 0))
				.with(csrf()))
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
		)).when(userService).signUp(any(), isNull());

		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("test@email.com", "password123", "tester", 3))
				.with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.EMAIL_ALREADY_EXISTS))
			.andExpect(jsonPath("$.message").value("중복된 이메일이 존재합니다."));
	}

	@Test
	void signUp_acceptsMultipartProfileImage() throws Exception {
		MockMultipartFile profileImage =
			new MockMultipartFile("profileImage", "profile.png", "image/png", "image".getBytes());

		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("test@email.com", "password123", "tester", 3))
				.file(profileImage)
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).signUp(any(), any());
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
		setAuthenticatedUser(1L, "test@email.com");
		org.mockito.Mockito.when(userService.getMyProfile(new LoginUserInfo(1L, "test@email.com")))
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
		setAuthenticatedUser(1L, "test@email.com");
		doThrow(new UserNotFoundException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNEXISTED_USER,
			"존재하지 않은 유저입니다."
		)).when(userService).getMyProfile(new LoginUserInfo(1L, "test@email.com"));

		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNEXISTED_USER))
			.andExpect(jsonPath("$.message").value("존재하지 않은 유저입니다."));
	}

	@Test
	void editMyProfile_returnsOk_whenRequestIsValid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(multipart("/api/users/me")
				.file(editProfileRequestPart("updated-description", "updated-nickname", 4))
				.with(request -> {
					request.setMethod("PUT");
					return request;
				})
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).editMyProfile(new LoginUserInfo(1L, "test@email.com"), null,
			new EditProfileRequest("updated-description", "updated-nickname", 4));
	}

	@Test
	void editMyProfile_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(multipart("/api/users/me")
				.file(editProfileRequestPart("updated-description", "", 0))
				.with(request -> {
					request.setMethod("PUT");
					return request;
				})
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.nickname").value("닉네임 입력은 필수입니다."))
			.andExpect(jsonPath("$.fieldErrors.level").value("레벨은 1 이상이어야 합니다."));
	}

	@Test
	void editMyProfile_returnsUnauthorized_whenAuthenticatedUserDoesNotExist() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");
		doThrow(new UserNotFoundException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNEXISTED_USER,
			"존재하지 않은 유저입니다."
		)).when(userService).editMyProfile(
			new LoginUserInfo(1L, "test@email.com"),
			null,
			new EditProfileRequest("updated-description", "updated-nickname", 4)
		);

		mockMvc.perform(multipart("/api/users/me")
				.file(editProfileRequestPart("updated-description", "updated-nickname", 4))
				.with(request -> {
					request.setMethod("PUT");
					return request;
				})
				.with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.UNEXISTED_USER))
			.andExpect(jsonPath("$.message").value("존재하지 않은 유저입니다."));
	}

	@Test
	void editPassword_returnsOk_whenRequestIsValid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me/edit-password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"password123","afterPassword":"newPassword123"}
					"""))
			.andExpect(status().isOk());

		verify(userService).editPassword(
			new LoginUserInfo(1L, "test@email.com"),
			new EditPasswordRequest("password123", "newPassword123")
		);
	}

	@Test
	void editPassword_returnsBadRequest_whenRequestIsInvalid() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(put("/api/users/me/edit-password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"currentPassword":"","afterPassword":"123"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.currentPassword").value("현재 비밀번호 입력은 필수입니다."))
			.andExpect(jsonPath("$.fieldErrors.afterPassword").value("비밀번호는 8글자 이상이어야 합니다."));
	}

	@Test
	void editPassword_returnsUnauthorized_whenPasswordDoesNotMatch() throws Exception {
		setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidPasswordException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNMATCHED_PASSWORD,
			"현재 비밀번호와 동일하지 않습니다."
		)).when(userService).editPassword(
			new LoginUserInfo(1L, "test@email.com"),
			new EditPasswordRequest("password123", "newPassword123")
		);

		mockMvc.perform(put("/api/users/me/edit-password")
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
		setAuthenticatedUser(1L, "test@email.com");

		mockMvc.perform(delete("/api/users/me")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"password123"}
					"""))
			.andExpect(status().isOk());

		verify(userService).deleteUser(
			new LoginUserInfo(1L, "test@email.com"),
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
		setAuthenticatedUser(1L, "test@email.com");
		doThrow(new InvalidPasswordException(
			HttpStatus.UNAUTHORIZED,
			ErrorCodeConstants.UNMATCHED_PASSWORD,
			"현재 비밀번호와 동일하지 않습니다."
		)).when(userService).deleteUser(
			new LoginUserInfo(1L, "test@email.com"),
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

	private MockMultipartFile signUpRequestPart(String email, String password, String nickname, Integer level) {
		String json = """
			{"email":"%s","password":"%s","nickname":"%s","level":%s}
			""".formatted(email, password, nickname, level == null ? "null" : level);

		return new MockMultipartFile(
			"signUpRequest",
			"",
			MediaType.APPLICATION_JSON_VALUE,
			json.getBytes(StandardCharsets.UTF_8)
		);
	}

	private MockMultipartFile editProfileRequestPart(String description, String nickname, Integer level) {
		String json = """
			{"description":%s,"nickname":%s,"level":%s}
			""".formatted(
			description == null ? "null" : "\"%s\"".formatted(description),
			nickname == null ? "null" : "\"%s\"".formatted(nickname),
			level == null ? "null" : level
		);

		return new MockMultipartFile(
			"EditProfileRequest",
			"",
			MediaType.APPLICATION_JSON_VALUE,
			json.getBytes(StandardCharsets.UTF_8)
		);
	}

	private void setAuthenticatedUser(Long id, String email) {
		UserPrincipal principal = new UserPrincipal(id, email);
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
