package team.po.feature.user.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class DuplicatedEmailException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;

	public DuplicatedEmailException(HttpStatus code, String error, String message) {
		this.code = code;
		this.error = error;
		this.message = message;
	}
}
