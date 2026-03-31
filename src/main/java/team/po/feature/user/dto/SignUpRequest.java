package team.po.feature.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignUpRequest(@NotBlank(message = "이메일 입력은 필수입니다.")
							@Email(message = "이메일 형식이 올바르지 않습니다.")
							String email,
							@NotBlank(message = "비밀번호 입력은 필수입니다.")
							@Size(min = 8,message = "비밀번호는 8글자 이상이어야 합니다.")
							String password,
							@NotBlank(message = "닉네임 입력은 필수입니다.")
							String nickname,
							@NotNull(message = "레벨 선택은 필수입니다.")
							@Min(value = 1, message = "레벨은 1 이상이어야 합니다.")
							@Max(value = 5, message = "레벨은 5 이하여야 합니다.")
							Integer level) {
}
