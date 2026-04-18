package team.po.feature.match.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class InvalidMatchStatusException extends RuntimeException {
	private final String message;
}
