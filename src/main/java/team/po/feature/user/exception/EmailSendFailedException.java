package team.po.feature.user.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class EmailSendFailedException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;

	public EmailSendFailedException(HttpStatus code, String error, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.error = error;
		this.message = message;
	}
}
