package team.po.feature.match.dto;

import java.util.List;

import team.po.feature.match.enums.Role;

public record MatchMemberResponse(
	Long matchId,
	List<MemberDto> members
) {
	public record MemberDto(
		Long userId,
		String nickname,
		Role role,
		int level,
		int temperature,
		String profileImageKey,
		boolean isHost,
		Boolean isAccepted
	) {
	}
}
