package team.po.exception;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestCancelNotAllowedException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;

@RestControllerAdvice
public class CustomProjectRequestExceptionHandler {
	@ExceptionHandler(ProjectRequestNotFoundException.class)
	protected ResponseEntity<ExceptionResponse> projectRequestNotFoundException(ProjectRequestNotFoundException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(ProjectRequestAlreadyExistsException.class)
	protected ResponseEntity<ExceptionResponse> projectRequestAlreadyExistsException(
		ProjectRequestAlreadyExistsException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}

	@ExceptionHandler(ProjectRequestCancelNotAllowedException.class)
	protected ResponseEntity<ExceptionResponse> projectRequestCancelNotAllowedException(
		ProjectRequestCancelNotAllowedException e) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(new ExceptionResponse(ErrorCodeConstants.PROJECT_REQUEST_CANCEL_NOT_ALLOWED, "취소할 수 없는 상태입니다.",
				Optional.empty()));
	}
}
