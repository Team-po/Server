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
import team.po.feature.user.exception.EmailNotVerifiedException;
import team.po.feature.user.exception.EmailSendFailedException;
import team.po.feature.user.exception.InvalidAuthenticationException;
import team.po.feature.user.exception.InvalidEmailAuthCodeException;
import team.po.feature.user.exception.InvalidImageContentTypeException;
import team.po.feature.user.exception.InvalidPasswordException;
import team.po.feature.user.exception.InvalidProfileImageKeyException;
import team.po.feature.user.exception.InvalidTokenException;
import team.po.feature.user.exception.UserNotFoundException;

@RestControllerAdvice
public class CustomExceptionHandler {
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
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	protected ResponseEntity<ExceptionResponse> badCredentialsException(BadCredentialsException e) {
		return ResponseEntity.status(401)
			.body(new ExceptionResponse(ErrorCodeConstants.INVALID_CREDENTIALS, e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidTokenException.class)
	protected ResponseEntity<ExceptionResponse> invalidTokenException(InvalidTokenException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidAuthenticationException.class)
	protected ResponseEntity<ExceptionResponse> invalidAuthenticationException(InvalidAuthenticationException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidPasswordException.class)
	protected ResponseEntity<ExceptionResponse> invalidPasswordException(InvalidPasswordException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	protected ResponseEntity<ExceptionResponse> userNotFoundException(UserNotFoundException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidImageContentTypeException.class)
	protected ResponseEntity<ExceptionResponse> invalidImageContentTypeException(InvalidImageContentTypeException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidProfileImageKeyException.class)
	protected ResponseEntity<ExceptionResponse> invalidProfileImageKeyException(InvalidProfileImageKeyException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(InvalidEmailAuthCodeException.class)
	protected ResponseEntity<ExceptionResponse> invalidEmailAuthCodeException(InvalidEmailAuthCodeException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(EmailSendFailedException.class)
	protected ResponseEntity<ExceptionResponse> emailSendFailedException(EmailSendFailedException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(EmailNotVerifiedException.class)
	protected ResponseEntity<ExceptionResponse> emailNotVerifiedException(EmailNotVerifiedException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}
}
