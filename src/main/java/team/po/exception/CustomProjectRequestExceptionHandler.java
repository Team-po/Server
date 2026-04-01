package team.po.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team.po.feature.match.exception.ProjectRequestAlreadyExistsException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;

import java.util.Optional;

@RestControllerAdvice
public class CustomProjectRequestExceptionHandler {
    @ExceptionHandler(ProjectRequestNotFoundException.class)
    protected ResponseEntity<ExceptionResponse> projectRequestNotFoundException(ProjectRequestNotFoundException e) {
        return ResponseEntity.status(e.getCode())
                .body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
    }

    @ExceptionHandler(ProjectRequestAlreadyExistsException.class)
    protected ResponseEntity<ExceptionResponse> projectRequestAlreadyExistsException(ProjectRequestAlreadyExistsException e) {
        return ResponseEntity.status(e.getCode())
                .body(new ExceptionResponse(e.getError(), e.getMessage(), Optional.empty()));
    }
}
