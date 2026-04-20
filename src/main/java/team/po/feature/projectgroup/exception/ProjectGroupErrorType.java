package team.po.feature.projectgroup.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import team.po.exception.ErrorCodeConstants;

@Getter
@RequiredArgsConstructor
public enum ProjectGroupErrorType {
	PROJECT_GROUP_PERMISSION_DENIED(
		HttpStatus.FORBIDDEN,
		ErrorCodeConstants.PROJECT_GROUP_PERMISSION_DENIED,
		"본인만 방장으로 팀 스페이스를 생성할 수 있습니다."
	),
	PROJECT_GROUP_MEMBER_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		ErrorCodeConstants.PROJECT_GROUP_MEMBER_NOT_FOUND,
		"매칭 사용자 중 존재하지 않는 사용자가 있습니다."
	),
	INVALID_PROJECT_GROUP_REQUEST(
		HttpStatus.BAD_REQUEST,
		ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST,
		"팀 스페이스 생성 요청이 올바르지 않습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;
}
