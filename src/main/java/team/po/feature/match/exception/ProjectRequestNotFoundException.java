package team.po.feature.match.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProjectRequestNotFoundException extends RuntimeException {
    private final HttpStatus code;
    private final String error;
    private final String message;

    public ProjectRequestNotFoundException(HttpStatus code, String error, String message) {
        this.code = code;
        this.error = error;
        this.message = message;
    }
}
