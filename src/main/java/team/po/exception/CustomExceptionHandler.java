package team.po.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class CustomExceptionHandler {
	@ExceptionHandler(MethodArgumentNotValidException.class)
	protected ResponseEntity<ExceptionResponse> methodArgumentNotValidException(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();

		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ExceptionResponse.from(ErrorCode.INVALID_INPUT_FIELD, fieldErrors));
	}

	@ExceptionHandler(ApplicationException.class)
	protected ResponseEntity<ExceptionResponse> applicationException(ApplicationException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(ExceptionResponse.from(exception));
	}

	@ExceptionHandler(BadCredentialsException.class)
	protected ResponseEntity<ExceptionResponse> badCredentialsException(BadCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ExceptionResponse.from(ErrorCode.INVALID_CREDENTIALS, exception.getMessage()));
	}
}
