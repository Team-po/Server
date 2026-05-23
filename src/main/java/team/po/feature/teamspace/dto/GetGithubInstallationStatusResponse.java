package team.po.feature.teamspace.dto;

public record GetGithubInstallationStatusResponse(
	boolean connected,
	String organizationLogin,
	long repositoryCount
) {
	public static GetGithubInstallationStatusResponse disconnected() {
		return new GetGithubInstallationStatusResponse(false, null, 0);
	}

	public static GetGithubInstallationStatusResponse connected(String organizationLogin, long repositoryCount) {
		return new GetGithubInstallationStatusResponse(
			true,
			organizationLogin,
			repositoryCount
		);
	}
}
