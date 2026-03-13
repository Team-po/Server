package team.po.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import team.po.exception.CustomExceptionHandler;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void signUp_returnsOk_whenRequestIsValid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.param("email", "test@email.com")
				.param("password", "password123")
				.param("nickname", "tester")
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).signUp(any(), isNull());
	}

	@Test
	void signUp_returnsBadRequestWithFieldErrors_whenRequestIsInvalid() throws Exception {
		mockMvc.perform(multipart("/api/users/sign-up")
				.param("email", "invalid-email")
				.param("password", "123")
				.param("nickname", "")
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
				.param("email", "test@email.com")
				.param("password", "password123")
				.param("nickname", "tester")
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
				.file(profileImage)
				.param("email", "test@email.com")
				.param("password", "password123")
				.param("nickname", "tester")
				.with(csrf()))
			.andExpect(status().isOk());

		verify(userService).signUp(any(), any());
	}
}
