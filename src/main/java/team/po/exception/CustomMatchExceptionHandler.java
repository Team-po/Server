package team.po.exception;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import team.po.feature.match.exception.MatchAccessDeniedException;

@RestControllerAdvice
public class CustomMatchExceptionHandler {
	@ExceptionHandler(MatchAccessDeniedException.class)
	protected ResponseEntity<ExceptionResponse> matchAccessDeniedException(MatchAccessDeniedException e) {
		return ResponseEntity.status(e.getCode())
			.body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
	}
}
