package team.po.exception;

public final class ErrorCodeConstants extends RuntimeException {
	private ErrorCodeConstants() {}

	public static final String INVALID_INPUT_FIELD = "INVALID_INPUT_FIELD";
	public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
	public static final String INVALID_TOKEN = "INVALID_TOKEN";
	public static final String UNEXISTED_USER = "UNEXISTED_USER";
}
