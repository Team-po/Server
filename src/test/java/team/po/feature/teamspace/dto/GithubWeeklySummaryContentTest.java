package team.po.feature.teamspace.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

class GithubWeeklySummaryContentTest {

	@Test
	void validate_allowsEmptyLists_whenSummaryExists() {
		GithubWeeklySummaryContent content = new GithubWeeklySummaryContent(
			"최근 7일간 확인된 GitHub 활동이 없습니다.",
			List.of(),
			List.of(),
			List.of(),
			List.of()
		);

		assertThatCode(content::validate).doesNotThrowAnyException();
	}

	@Test
	void validate_throwsInvalidResponse_whenSummaryIsBlank() {
		GithubWeeklySummaryContent content = new GithubWeeklySummaryContent(
			" ",
			List.of(),
			List.of(),
			List.of(),
			List.of()
		);

		assertThatThrownBy(content::validate)
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GEMINI_INVALID_RESPONSE.getCode());
	}

	@Test
	void validate_throwsInvalidResponse_whenListHasTooManyItems() {
		GithubWeeklySummaryContent content = new GithubWeeklySummaryContent(
			"이번 주에는 API 개선 작업이 진행되었습니다.",
			List.of("1", "2", "3", "4", "5", "6"),
			List.of(),
			List.of(),
			List.of()
		);

		assertThatThrownBy(content::validate)
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GEMINI_INVALID_RESPONSE.getCode());
	}
}
