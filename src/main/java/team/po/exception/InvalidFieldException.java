package team.po.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class InvalidFieldException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;
	private final Errors errors;
}
