package team.po.exception;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import team.po.feature.projectgroup.exception.ProjectGroupException;

@RestControllerAdvice
public class CustomTeamExceptionHandler {

	@ExceptionHandler(ProjectGroupException.class)
	protected ResponseEntity<ExceptionResponse> projectGroupException(ProjectGroupException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}
}
