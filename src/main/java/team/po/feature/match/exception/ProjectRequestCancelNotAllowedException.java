package team.po.feature.match.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProjectRequestCancelNotAllowedException extends RuntimeException { }
