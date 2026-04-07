package team.po.feature.user.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class InvalidTokenException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;
}
