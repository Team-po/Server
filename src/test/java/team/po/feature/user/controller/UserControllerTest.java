package team.po.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import team.po.exception.CustomUserExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomUserExceptionHandler.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void signUp_returnsOk_whenRequestIsValid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("test@email.com", "password123", "tester"))
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).signUp(any(), isNull());
	}

	@Test
	void signUp_returnsBadRequestWithFieldErrors_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("invalid-email", "123", ""))
				.with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCodeConstants.INVALID_INPUT_FIELD))
			.andExpect(jsonPath("$.fieldErrors.email").value("이메일 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.fieldErrors.password").value("비밀번호는 8글자 이상이어야 합니다."))
			.andExpect(jsonPath("$.fieldErrors.nickname").value("닉네임 입력은 필수입니다."));
	}

	@Test
	void signUp_returnsConflict_whenEmailAlreadyExists() throws Exception {
		doThrow(new DuplicatedEmailException(
			HttpStatus.CONFLICT,
			ErrorCodeConstants.EMAIL_ALREADY_EXISTS,
			"중복된 이메일이 존재합니다."
		)).when(userService).signUp(any(), isNull());

		mockMvc.perform(multipart("/api/users/sign-up")
				.file(signUpRequestPart("test@email.com", "password123", "tester"))
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
				.file(signUpRequestPart("test@email.com", "password123", "tester"))
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

	private MockMultipartFile signUpRequestPart(String email, String password, String nickname) {
		String json = """
			{"email":"%s","password":"%s","nickname":"%s"}
			""".formatted(email, password, nickname);

		return new MockMultipartFile(
			"signUpRequest",
			"",
			MediaType.APPLICATION_JSON_VALUE,
			json.getBytes(StandardCharsets.UTF_8)
		);
	}
}
