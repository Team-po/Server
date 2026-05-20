package team.po.feature.teamspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompleteGithubAppInstallationRequest(
	@NotNull
	Long installationId,
	@NotBlank
	String setupAction,
	@NotBlank
	String state
) {
}
