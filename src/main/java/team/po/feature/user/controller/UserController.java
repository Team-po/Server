package team.po.feature.user.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {
	private final UserService userService;

	@Operation(summary = "회원가입")
	@PostMapping(value = "/users/sign-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> signUp(@Valid @RequestPart SignUpRequest request, Errors errors,
		@RequestPart(required = false) MultipartFile profileImage) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD, "입력값이 올바르지 않습니다.", errors);
		}

		userService.signUp(request, profileImage);
		return ResponseEntity.ok().build();
	}
}
