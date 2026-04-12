package team.po.feature.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.DeleteUserRequest;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.RefreshTokenRequest;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;

	@Operation(summary = "회원 가입 API")
	@PostMapping(value = "/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> signUp(@Valid @RequestPart("signUpRequest") SignUpRequest signUpRequest,
		Errors errors, @RequestPart(required = false) MultipartFile profileImage) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}

		userService.signUp(signUpRequest, profileImage);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "이메일 중복 검사 API")
	@GetMapping(value = "/check-email")
	public ResponseEntity<Void> checkEmailDuplicate(@RequestParam String email) {
		userService.checkEmailDuplication(email);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "로그인 API")
	@PostMapping(value = "/sign-in")
	public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest request, Errors errors) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}
		SignInResponse response = userService.signIn(request);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "토큰 재발급 API")
	@PostMapping(value = "/refresh-token")
	public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
		Errors errors) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}
		RefreshTokenResponse response = userService.refreshToken(request);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "유저 프로필 조회 API")
	@GetMapping(value = "/me")
	public ResponseEntity<GetProfileResponse> getMyProfile(@Parameter(hidden = true) @LoginUser Users user) {
		GetProfileResponse response = userService.getMyProfile(user);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "유저 프로필 수정 API")
	@PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> edidMyProfile(@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestPart("EditProfileRequest") EditProfileRequest editProfileRequest,
		Errors errors, @RequestPart(required = false) MultipartFile profileImage) {

		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}

		userService.editMyProfile(user, profileImage, editProfileRequest);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "비밀번호 수정 API")
	@PutMapping(value = "/me/password")
	public ResponseEntity<Void> editPassword(@Parameter(hidden = true) @LoginUser Users user, @Valid @RequestBody
	EditPasswordRequest request, Errors errors) {

		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}

		userService.editPassword(user, request);

		return ResponseEntity.ok().build();
	}

	@Operation(summary = "회원 탈퇴 API")
	@DeleteMapping(value = "/me")
	public ResponseEntity<Void> deleteUser(@Parameter(hidden = true) @LoginUser Users user, @Valid @RequestBody
	DeleteUserRequest request, Errors errors) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}

		userService.deleteUser(user, request);

		return ResponseEntity.ok().build();
	}

}
