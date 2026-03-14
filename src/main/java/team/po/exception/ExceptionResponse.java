package team.po.exception;

import java.util.Map;
import java.util.Optional;

public record ExceptionResponse(
	String code, String message, Optional<Map<String, String>> fieldErrors
) {
}
