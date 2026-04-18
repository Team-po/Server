package team.po.exception;

public final class ErrorCodeConstants extends RuntimeException {
	private ErrorCodeConstants() {
	}

	public static final String INVALID_INPUT_FIELD = "INVALID_INPUT_FIELD";
	public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
	public static final String INVALID_TOKEN = "INVALID_TOKEN";
	public static final String UNEXISTED_USER = "UNEXISTED_USER";
	public static final String NO_AUTHENTICATED_USER = "NO_AUTHENTICATED_USER";
	public static final String INVALID_SECURITY_CONTEXT = "INVALID_SECURITY_CONTEXT";
	public static final String UNMATCHED_PASSWORD = "UNMATCHED_PASSWORD";
	public static final String INVALID_IMAGE_CONTENT_TYPE = "INVALID_IMAGE_CONTENT_TYPE";
	public static final String INVALID_PROFILE_IMAGE_KEY = "INVALID_PROFILE_IMAGE_KEY";

	// ProjectRequest
	public static final String PROJECT_REQUEST_NOT_FOUND = "PROJECT_REQUEST_NOT_FOUND";
	public static final String PROJECT_REQUEST_ALREADY_EXISTS = "PROJECT_REQUEST_ALREADY_EXISTS";
	public static final String PROJECT_REQUEST_CANCEL_NOT_ALLOWED = "PROJECT_REQUEST_CANCEL_NOT_ALLOWED";

	// Match
	public static final String MATCH_NOT_FOUND = "MATCH_NOT_FOUND";
	public static final String MATCH_ACCESS_DENIED = "MATCH_ACCESS_DENIED";
	public static final String MATCH_DATA_ERROR = "MATCH_DATA_ERROR";
	public static final String INVALID_MATCH_STATUS = "INVALID_MATCH_STATUS";
}
