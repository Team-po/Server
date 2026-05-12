package team.po.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	INVALID_INPUT_FIELD(HttpStatus.BAD_REQUEST, "INVALID_INPUT_FIELD", "입력값이 올바르지 않습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "중복된 이메일이 존재합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
	UNEXISTED_USER(HttpStatus.UNAUTHORIZED, "UNEXISTED_USER", "존재하지 않은 유저입니다."),
	NO_AUTHENTICATED_USER(HttpStatus.UNAUTHORIZED, "NO_AUTHENTICATED_USER", "인증된 유저를 찾을 수 없습니다."),
	INVALID_SECURITY_CONTEXT(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_SECURITY_CONTEXT",
		"인증된 사용자 정보를 해석할 수 없습니다."),
	UNMATCHED_PASSWORD(HttpStatus.UNAUTHORIZED, "UNMATCHED_PASSWORD", "현재 비밀번호와 동일하지 않습니다."),
	INVALID_IMAGE_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_CONTENT_TYPE", "지원하지 않는 이미지 형식입니다."),
	INVALID_PROFILE_IMAGE_KEY(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_IMAGE_KEY", "발급되지 않았거나 만료된 프로필 이미지 키입니다."),
	INVALID_EMAIL_AUTH_CODE(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_AUTH_CODE", "인증번호가 만료되었거나 올바르지 않습니다."),
	EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다."),
	EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED", "인증번호 이메일 발송에 실패했습니다."),
	INVALID_PROJECT_GROUP_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_GROUP_REQUEST", "팀 스페이스 생성 요청이 올바르지 않습니다."),
	PROJECT_GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_GROUP_MEMBER_NOT_FOUND",
		"매칭 사용자 중 존재하지 않는 사용자가 있습니다."),
	PROJECT_GROUP_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "PROJECT_GROUP_PERMISSION_DENIED",
		"본인만 방장으로 팀 스페이스를 생성할 수 있습니다."),
	PROJECT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_REQUEST_NOT_FOUND", "진행 중인 매칭 요청이 없습니다."),
	PROJECT_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROJECT_REQUEST_ALREADY_EXISTS", "이미 진행 중인 매칭 요청이 있습니다."),
	PROJECT_REQUEST_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PROJECT_REQUEST_CANCEL_NOT_ALLOWED", "취소할 수 없는 상태입니다."),
	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "이미 완료되었거나 존재하지 않는 매칭 세션입니다."),
	MATCH_ACCESS_DENIED(HttpStatus.BAD_REQUEST, "MATCH_ACCESS_DENIED", "매칭 세션 접근 권한이 없습니다."),
	INVALID_MATCH_STATUS(HttpStatus.BAD_REQUEST, "INVALID_MATCH_STATUS", "매칭 상태가 유효하지 않습니다."),
	MATCH_DATA_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "MATCH_DATA_ERROR", "매칭 데이터가 정합하지 않습니다."),
	GUIDELINE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "GUIDELINE_GENERATION_FAILED",
		" AI 가이드라인 생성에 실패했습니다."),
	GEMINI_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI_INVALID_RESPONSE",
		"Gemini API 응답 형식이 올바르지 않습니다."),
	GEMINI_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI_API_ERROR", "Gemini API 호출에 실패했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
