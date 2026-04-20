package team.po.feature.projectgroup.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ProjectGroupException extends RuntimeException {
	private final ProjectGroupErrorType errorType;

	public ProjectGroupException(ProjectGroupErrorType errorType) {
		this(errorType, errorType.getMessage());
	}

	public ProjectGroupException(ProjectGroupErrorType errorType, String message) {
		super(message);
		this.errorType = errorType;
	}

	public HttpStatus getCode() {
		return errorType.getStatus();
	}

	public String getError() {
		return errorType.getCode();
	}
}
