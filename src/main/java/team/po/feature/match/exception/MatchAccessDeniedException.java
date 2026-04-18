package team.po.feature.match.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MatchAccessDeniedException extends RuntimeException {
	private final HttpStatus code;
	private final String error;
	private final String message;
}
