package team.po.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {
	private final ErrorCode errorCode;

	public ApplicationException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public ApplicationException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ApplicationException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	public ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public HttpStatus getStatus() {
		return errorCode.getStatus();
	}

	public String getCode() {
		return errorCode.getCode();
	}
}
