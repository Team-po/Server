package team.po.feature.teamspace.service;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.RequiredArgsConstructor;
import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.teamspace.dto.GithubPullRequestInfo;
import team.po.feature.teamspace.dto.GithubPullRequestSummary;
import team.po.feature.teamspace.provider.GithubAppJwtProvider;

@RequiredArgsConstructor
@Component
public class GithubAppClient {
	private static final String GITHUB_API_VERSION = "2022-11-28";
	private static final String ORGANIZATION_MEMBERSHIP_ACTIVE_STATE = "active";
	private static final String ORGANIZATION_MEMBERSHIP_ADMIN_ROLE = "admin";
	private static final int REPOSITORY_PAGE_SIZE = 100;
	private static final int PULL_REQUEST_PAGE_SIZE = 100;
	private static final Pattern CLOSING_ISSUE_REFERENCES_PATTERN = Pattern.compile(
		"(?i)\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\b\\s+"
			+ "((?:(?:[\\w.-]+/[\\w.-]+)?#\\d+)(?:\\s*(?:,|and)\\s*(?:(?:[\\w.-]+/[\\w.-]+)?#\\d+))*)"
	);
	private static final Pattern ISSUE_REFERENCE_PATTERN = Pattern.compile("(?:[\\w.-]+/[\\w.-]+)?#\\d+");

	private final RestClient restClient;
	private final GithubAppJwtProvider githubAppJwtProvider;
	private final GithubAppProperties githubAppProperties;

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

	public List<GithubRepositoryInfo> getInstallationRepositories(Long installationId) {
		String accessToken = createInstallationAccessToken(installationId);
		List<GithubRepositoryInfo> repositories = new ArrayList<>();
		int page = 1;

		while (true) {
			GithubInstallationRepositoriesResponse response = getInstallationRepositories(accessToken, page);
			if (response.repositories() == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			response.repositories().stream()
				.map(repository -> new GithubRepositoryInfo(
					repository.id(),
					repository.owner().login(),
					repository.name(),
					repository.fullName(),
					repository.defaultBranch(),
					repository.privateRepository()
				))
				.forEach(repositories::add);

			if (response.repositories().size() < REPOSITORY_PAGE_SIZE) {
				return repositories;
			}
			page++;
		}
	}

	public List<GithubPullRequestSummary> getClosedPullRequests(Long installationId, String owner, String repoName) {
		String accessToken = createInstallationAccessToken(installationId);
		List<GithubPullRequestSummary> pullRequests = new ArrayList<>();
		int page = 1;

		while (true) {
			List<GithubPullRequestResponse> response = getClosedPullRequests(accessToken, owner, repoName, page);

			response.stream()
				.filter(this::hasGithubUser)
				.map(pullRequest -> new GithubPullRequestSummary(
					pullRequest.id(),
					pullRequest.number(),
					pullRequest.title(),
					pullRequest.user().id(),
					pullRequest.user().login(),
					pullRequest.state(),
					pullRequest.mergedAt(),
					pullRequest.htmlUrl()
				))
				.forEach(pullRequests::add);

			if (response.size() < PULL_REQUEST_PAGE_SIZE) {
				return pullRequests;
			}
			page++;
		}
	}

	public Optional<GithubPullRequestInfo> getPullRequest(
		Long installationId,
		String owner,
		String repoName,
		Long pullNumber
	) {
		String accessToken = createInstallationAccessToken(installationId);
		GithubPullRequestResponse response = getPullRequest(accessToken, owner, repoName, pullNumber);
		if (!hasGithubUser(response)) {
			return Optional.empty();
		}

		return Optional.of(new GithubPullRequestInfo(
			response.id(),
			response.number(),
			response.title(),
			response.user().id(),
			response.user().login(),
			response.state(),
			response.mergedAt(),
			response.additions(),
			response.deletions(),
			response.changedFiles(),
			countLinkedIssues(response.body()),
			response.htmlUrl()
		));
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

	private String createInstallationAccessToken(Long installationId) {
		try {
			GithubInstallationAccessTokenResponse response = restClient.post()
				.uri(githubAppProperties.apiBaseUrl() + "/app/installations/{installationId}/access_tokens",
					installationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + githubAppJwtProvider.generateJwt())
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(GithubInstallationAccessTokenResponse.class);

			if (response == null || response.token() == null || response.token().isBlank()) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			return response.token();
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

	private GithubInstallationRepositoriesResponse getInstallationRepositories(String accessToken, int page) {
		try {
			GithubInstallationRepositoriesResponse response = restClient.get()
				.uri(UriComponentsBuilder
					.fromUriString(githubAppProperties.apiBaseUrl())
					.pathSegment("installation", "repositories")
					.queryParam("per_page", REPOSITORY_PAGE_SIZE)
					.queryParam("page", page)
					.build()
					.toUriString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(GithubInstallationRepositoriesResponse.class);

			if (response == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			return response;
		} catch (ApplicationException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		}
	}

	private List<GithubPullRequestResponse> getClosedPullRequests(
		String accessToken,
		String owner,
		String repoName,
		int page
	) {
		try {
			List<GithubPullRequestResponse> response = restClient.get()
				.uri(UriComponentsBuilder
					.fromUriString(githubAppProperties.apiBaseUrl())
					.pathSegment("repos", owner, repoName, "pulls")
					.queryParam("state", "closed")
					.queryParam("per_page", PULL_REQUEST_PAGE_SIZE)
					.queryParam("page", page)
					.build()
					.toUriString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});

			if (response == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			return response;
		} catch (ApplicationException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		}
	}

	private GithubPullRequestResponse getPullRequest(
		String accessToken,
		String owner,
		String repoName,
		Long pullNumber
	) {
		try {
			GithubPullRequestResponse response = restClient.get()
				.uri(UriComponentsBuilder
					.fromUriString(githubAppProperties.apiBaseUrl())
					.pathSegment("repos", owner, repoName, "pulls", String.valueOf(pullNumber))
					.build()
					.toUriString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.header("X-GitHub-Api-Version", GITHUB_API_VERSION)
				.retrieve()
				.body(GithubPullRequestResponse.class);

			if (response == null) {
				throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED);
			}

			return response;
		} catch (ApplicationException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new ApplicationException(ErrorCode.GITHUB_API_REQUEST_FAILED, exception);
		}
	}

	private boolean hasGithubUser(GithubPullRequestResponse pullRequest) {
		return pullRequest.user() != null
			&& pullRequest.user().id() != null
			&& pullRequest.user().login() != null
			&& !pullRequest.user().login().isBlank();
	}

	private int countLinkedIssues(String pullRequestBody) {
		if (pullRequestBody == null || pullRequestBody.isBlank()) {
			return 0;
		}

		Set<String> issueReferences = new HashSet<>();
		Matcher closingIssueReferencesMatcher = CLOSING_ISSUE_REFERENCES_PATTERN.matcher(pullRequestBody);
		while (closingIssueReferencesMatcher.find()) {
			Matcher issueReferenceMatcher = ISSUE_REFERENCE_PATTERN.matcher(closingIssueReferencesMatcher.group(1));
			while (issueReferenceMatcher.find()) {
				issueReferences.add(issueReferenceMatcher.group().toLowerCase());
			}
		}

		return issueReferences.size();
	}

	public record GithubAppInstallationInfo(
		Long installationId,
		Long accountId,
		String accountLogin,
		String accountType
	) {
	}

	public record GithubRepositoryInfo(
		Long githubRepositoryId,
		String owner,
		String repoName,
		String fullName,
		String defaultBranch,
		boolean privateRepository
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

	private record GithubInstallationAccessTokenResponse(
		String token
	) {
	}

	private record GithubInstallationRepositoriesResponse(
		List<GithubRepositoryResponse> repositories
	) {
	}

	private record GithubRepositoryResponse(
		Long id,
		String name,
		@JsonProperty("full_name")
		String fullName,
		@JsonProperty("default_branch")
		String defaultBranch,
		@JsonProperty("private")
		boolean privateRepository,
		GithubRepositoryOwnerResponse owner
	) {
	}

	private record GithubRepositoryOwnerResponse(
		String login
	) {
	}

	private record GithubPullRequestResponse(
		Long id,
		Long number,
		String title,
		GithubPullRequestUserResponse user,
		String state,
		@JsonProperty("merged_at")
		Instant mergedAt,
		Integer additions,
		Integer deletions,
		@JsonProperty("changed_files")
		Integer changedFiles,
		String body,
		@JsonProperty("html_url")
		String htmlUrl
	) {
	}

	private record GithubPullRequestUserResponse(
		Long id,
		String login
	) {
	}
}
