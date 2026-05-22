package team.po.feature.teamspace.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.teamspace.provider.GithubAppJwtProvider;

@Component
public class GithubAppClient {
	private static final String GITHUB_API_VERSION = "2022-11-28";
	private static final String ORGANIZATION_MEMBERSHIP_ACTIVE_STATE = "active";
	private static final String ORGANIZATION_MEMBERSHIP_ADMIN_ROLE = "admin";

	private final RestClient restClient;
	private final GithubAppJwtProvider githubAppJwtProvider;
	private final GithubAppProperties githubAppProperties;

	public GithubAppClient(
		RestClient restClient,
		GithubAppJwtProvider githubAppJwtProvider,
		GithubAppProperties githubAppProperties
	) {
		this.restClient = restClient;
		this.githubAppJwtProvider = githubAppJwtProvider;
		this.githubAppProperties = githubAppProperties;
	}

	public GithubAppInstallationInfo getInstallation(Long installationId) {
		try {
			GithubAppInstallationResponse response = restClient.get()
				.uri(githubAppProperties.apiBaseUrl() + "/app/installations/{installationId}", installationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + githubAppJwtProvider.generateJwt())
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(GithubAppInstallationResponse.class);

			if (response == null || response.account() == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			return new GithubAppInstallationInfo(
				response.id(),
				response.account().id(),
				response.account().login(),
				response.account().type()
			);
		} catch (ApplicationException exception) {
			throw exception;
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new ApplicationException(ErrorCode.GITHUB_APP_INSTALLATION_NOT_FOUND, exception);
			}
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		} catch (RestClientException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		}
	}

	public void validateOrganizationAdmin(String accessToken, String organizationLogin) {
		try {
			GithubOrganizationMembershipResponse response = restClient.get()
				.uri(UriComponentsBuilder
					.fromUriString(githubAppProperties.apiBaseUrl())
					.pathSegment("user", "memberships", "orgs", organizationLogin)
					.build()
					.toUriString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(GithubOrganizationMembershipResponse.class);

			if (response == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}
			if (ORGANIZATION_MEMBERSHIP_ACTIVE_STATE.equals(response.state())
				&& ORGANIZATION_MEMBERSHIP_ADMIN_ROLE.equals(response.role())) {
				return;
			}

			throw new ApplicationException(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED);
		} catch (ApplicationException exception) {
			throw exception;
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode() == HttpStatus.NOT_FOUND
				|| exception.getStatusCode() == HttpStatus.FORBIDDEN
				|| exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				throw new ApplicationException(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED, exception);
			}
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		} catch (RestClientException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		}
	}

	public record GithubAppInstallationInfo(
		Long installationId,
		Long accountId,
		String accountLogin,
		String accountType
	) {
	}

	private record GithubAppInstallationResponse(
		Long id,
		GithubAppInstallationAccountResponse account
	) {
	}

	private record GithubAppInstallationAccountResponse(
		Long id,
		String login,
		String type
	) {
	}

	private record GithubOrganizationMembershipResponse(
		String state,
		String role
	) {
	}
}
