package team.po.feature.user.dto;

import lombok.Builder;

@Builder
public record GetProfileResponse(String email, String profileImage, String description,
								 String nickname, Integer temperature, Integer level) {
}
