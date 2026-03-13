package team.po.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;

import lombok.Getter;

@Getter
public class InvalidFieldException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;
	private final Errors errors;

	public InvalidFieldException(HttpStatus code, String error, String message, Errors errors) {
		this.code = code;
		this.error = error;
		this.message = message;
		this.errors = errors;
	}
}
