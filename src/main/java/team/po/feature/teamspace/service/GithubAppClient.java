package team.po.feature.teamspace.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.teamspace.provider.GithubAppJwtProvider;

@Component
public class GithubAppClient {
	private static final String GITHUB_API_VERSION = "2022-11-28";

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
}
