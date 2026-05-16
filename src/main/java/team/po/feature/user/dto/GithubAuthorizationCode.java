package team.po.feature.user.dto;

public record GithubAuthorizationCode(
	String authorizationCode,
	boolean onboardingRequired
) {
}
