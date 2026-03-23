package team.po.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import team.po.feature.user.exception.DuplicatedEmailException;

@RestControllerAdvice
public class CustomUserExceptionHandler {
	@ExceptionHandler(InvalidFieldException.class)
	protected ResponseEntity<ExceptionResponse> invalidFieldException(InvalidFieldException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();

		for (FieldError fieldError : e.getErrors().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}

		ExceptionResponse response = new ExceptionResponse(
			e.getError(),
			e.getMessage(),
			Optional.of(fieldErrors)
		);

		return ResponseEntity.status(e.getCode()).body(response);
	}

	@ExceptionHandler(DuplicatedEmailException.class)
	protected ResponseEntity<ExceptionResponse> DuplicatedEmailException(DuplicatedEmailException e) {
		return ResponseEntity.status(e.getCode()).body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	protected ResponseEntity<ExceptionResponse> badCredentialsException(BadCredentialsException e) {
		return ResponseEntity.status(401)
			.body(new ExceptionResponse(ErrorCodeConstants.INVALID_CREDENTIALS, e.getMessage(), Optional.empty()));
	}
}
