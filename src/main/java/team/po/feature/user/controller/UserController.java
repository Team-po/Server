package team.po.feature.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {
	private final UserService userService;

	@Operation(summary = "회원 가입 API")
	@PostMapping(value = "/users/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> signUp(@Valid @RequestPart("signUpRequest") SignUpRequest signUpRequest,
		Errors errors, @RequestPart(required = false) MultipartFile profileImage) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD, "입력값이 올바르지 않습니다.", errors);
		}

		userService.signUp(signUpRequest, profileImage);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "로그인 API")
	@PostMapping(value = "/users/sign-in")
	public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest request) {
		SignInResponse response = userService.signIn(request);
		return ResponseEntity.ok().body(response);
	}
}
