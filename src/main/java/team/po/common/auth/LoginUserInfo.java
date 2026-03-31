package team.po.common.auth;

import team.po.common.jwt.UserPrincipal;

public record LoginUserInfo(Long id, String email) {

	public static LoginUserInfo from(UserPrincipal principal) {
		return new LoginUserInfo(principal.id(), principal.email());
	}
}
