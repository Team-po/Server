package team.po.exception;

import java.util.Map;

public record ExceptionResponse(
	String code, String message, Map<String, String> fieldErrors
) {
	public static ExceptionResponse from(ErrorCode errorCode) {
		return new ExceptionResponse(errorCode.getCode(), errorCode.getMessage(), null);
	}

	public static ExceptionResponse from(ErrorCode errorCode, String message) {
		return new ExceptionResponse(errorCode.getCode(), message, null);
	}

	public static ExceptionResponse from(ApplicationException exception) {
		return new ExceptionResponse(exception.getCode(), exception.getMessage(), null);
	}

	public static ExceptionResponse from(ErrorCode errorCode, Map<String, String> fieldErrors) {
		return new ExceptionResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
	}
}
