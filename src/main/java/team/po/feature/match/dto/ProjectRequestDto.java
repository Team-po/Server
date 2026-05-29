package team.po.feature.match.dto;

import org.springframework.util.StringUtils;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import team.po.feature.match.enums.Role;

public record ProjectRequestDto(
	@NotNull(message = "역할 선택은 필수입니다.")
	Role role,
	String projectTitle,
	String projectDescription,
	String projectMvp
) {
	@AssertTrue(message = "프로젝트 정보를 입력하면 제목, 설명, MVP를 모두 작성해야 합니다.")
	public boolean isValidInput() {
		boolean hasAny = StringUtils.hasText(projectTitle)
			|| StringUtils.hasText(projectDescription)
			|| StringUtils.hasText(projectMvp);

		if (hasAny) { // 하나라도 작성한 경우 내용을 모두 채워야 신청 가능
			return StringUtils.hasText(projectTitle)
				&& StringUtils.hasText(projectDescription)
				&& StringUtils.hasText(projectMvp);
		}

		// 아무것도 작성하지 않은 경우 - member
		return true;
	}
}