package team.po.feature.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.user.dto.SendEmailRequest;
import team.po.feature.user.dto.ValidateAuthNumberRequest;
import team.po.feature.user.service.EmailService;

@RestController
@RequiredArgsConstructor
@RequestMapping("api")
public class EmailController {
	private final EmailService emailService;

	@Operation(summary = "이메일 인증번호 전송 API")
	@PostMapping(value = "/signup/email")
	public ResponseEntity<Void> sendEmail(@Valid @RequestBody SendEmailRequest request, Errors errors) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}
		emailService.sendEmail(request);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "이메일 인증번호 검증 API")
	@PostMapping(value = "/signup/number-validation")
	public ResponseEntity<Void> validateAuthNumber(@Valid @RequestBody ValidateAuthNumberRequest request, Errors errors) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(HttpStatus.BAD_REQUEST, ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.", errors);
		}
		emailService.validateAuthNumber(request);
		return ResponseEntity.ok().build();
	}
}
