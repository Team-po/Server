package team.po.feature.user.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class InvalidEmailAuthCodeException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;
}
