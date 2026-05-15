package team.po.feature.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.ProfileImageUploadUrlRequest;
import team.po.feature.user.dto.ProfileImageUploadUrlResponse;
import team.po.feature.user.dto.RefreshTokenRequest;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.dto.ValidateDeleteUserEmailRequest;
import team.po.feature.user.service.ImageService;
import team.po.feature.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;
	private final ImageService imageService;

	@Operation(summary = "회원 가입 API")
	@PostMapping(value = "/sign-up")
	public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequest signUpRequest) {
		userService.signUp(signUpRequest);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "회원가입용 프로필 이미지 업로드 URL 발급 API")
	@PostMapping(value = "/profile-image/upload-url")
	public ResponseEntity<ProfileImageUploadUrlResponse> createSignUpProfileImageUploadUrl(
		@Valid @RequestBody ProfileImageUploadUrlRequest request
	) {
		ProfileImageUploadUrlResponse response = imageService.createSignUpUploadUrl(request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "이메일 중복 검사 API")
	@GetMapping(value = "/check-email")
	public ResponseEntity<Void> checkEmailDuplicate(@RequestParam String email) {
		userService.checkEmailDuplication(email);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "로그인 API")
	@PostMapping(value = "/sign-in")
	public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest request) {
		SignInResponse response = userService.signIn(request);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "토큰 재발급 API")
	@PostMapping(value = "/refresh-token")
	public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
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
	@PutMapping(value = "/me")
	public ResponseEntity<Void> edidMyProfile(@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestBody EditProfileRequest editProfileRequest) {
		userService.editMyProfile(user, editProfileRequest);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "프로필 수정용 프로필 이미지 업로드 URL 발급 API")
	@PostMapping(value = "/me/profile-image/upload-url")
	public ResponseEntity<ProfileImageUploadUrlResponse> createProfileImageUploadUrl(
		@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestBody ProfileImageUploadUrlRequest request
	) {
		ProfileImageUploadUrlResponse response = imageService.createProfileUploadUrl(user, request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "비밀번호 수정 API")
	@PutMapping(value = "/me/password")
	public ResponseEntity<Void> editPassword(
		@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestBody EditPasswordRequest request
	) {
		userService.editPassword(user, request);

		return ResponseEntity.ok().build();
	}

	@Operation(summary = "회원 탈퇴용 이메일 인증번호 전송 API")
	@PostMapping(value = "/me/deletion-email")
	public ResponseEntity<Void> sendDeleteUserEmail(@Parameter(hidden = true) @LoginUser Users user) {
		userService.sendDeleteUserEmail(user);

		return ResponseEntity.ok().build();
	}

	@Operation(summary = "회원 탈퇴용 이메일 인증번호 검증 API")
	@PostMapping(value = "/me/deletion-number-validation")
	public ResponseEntity<Void> validateDeleteUserEmail(
		@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestBody ValidateDeleteUserEmailRequest request
	) {
		userService.validateDeleteUserEmail(user, request);

		return ResponseEntity.ok().build();
	}

	@Operation(summary = "회원 탈퇴 API")
	@DeleteMapping(value = "/me")
	public ResponseEntity<Void> deleteUser(@Parameter(hidden = true) @LoginUser Users user) {
		userService.deleteUser(user);

		return ResponseEntity.ok().build();
	}

}
