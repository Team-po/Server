package team.po.feature.match.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public class ProjectRequestNotFoundException extends RuntimeException {
    private final HttpStatus code;
    private final String error;
    private final String message;
}
