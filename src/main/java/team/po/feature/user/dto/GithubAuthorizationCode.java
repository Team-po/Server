package team.po.feature.user.dto;

public record GithubAuthorizationCode(
	String code,
	boolean onboardingRequired
) {
}
