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
import team.po.feature.teamspace.dto.GithubRepositoryInfo;
import team.po.feature.teamspace.dto.GithubWeeklySummaryData;
import team.po.feature.teamspace.provider.GithubAppJwtProvider;

@RequiredArgsConstructor
@Component
public class GithubAppClient {
	private static final String GITHUB_API_VERSION = "2022-11-28";
	private static final String ORGANIZATION_MEMBERSHIP_ACTIVE_STATE = "active";
	private static final String ORGANIZATION_MEMBERSHIP_ADMIN_ROLE = "admin";
	private static final int REPOSITORY_PAGE_SIZE = 100;
	private static final int PULL_REQUEST_PAGE_SIZE = 100;
	private static final int ISSUE_PAGE_SIZE = 100;
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
		return createPullRequestSyncSession(installationId).getClosedPullRequests(owner, repoName);
	}

	public GithubPullRequestSyncSession createPullRequestSyncSession(Long installationId) {
		return new InstallationTokenPullRequestSyncSession(createInstallationAccessToken(installationId));
	}

	public GithubWeeklySummaryData getWeeklySummaryData(
		Long installationId,
		List<GithubRepositoryInfo> repositories,
		Long authorGithubUserId,
		Instant periodStart,
		Instant periodEnd
	) {
		String accessToken = createInstallationAccessToken(installationId);
		List<GithubWeeklySummaryData.RepositoryActivity> repositoryActivities = repositories.stream()
			.map(repository -> new GithubWeeklySummaryData.RepositoryActivity(
				repository.githubRepositoryId(),
				repository.owner(),
				repository.repoName(),
				repository.fullName(),
				getWeeklyPullRequests(accessToken, repository, authorGithubUserId, periodStart, periodEnd),
				getWeeklyIssues(accessToken, repository, authorGithubUserId, periodStart, periodEnd)
			))
			.toList();

		return new GithubWeeklySummaryData(periodStart, periodEnd, repositoryActivities);
	}

	private class InstallationTokenPullRequestSyncSession implements GithubPullRequestSyncSession {
		private final String accessToken;

		private InstallationTokenPullRequestSyncSession(String accessToken) {
			this.accessToken = accessToken;
		}

		@Override
		public List<GithubPullRequestSummary> getClosedPullRequests(String owner, String repoName) {
			return GithubAppClient.this.getClosedPullRequests(accessToken, owner, repoName);
		}

		@Override
		public Optional<GithubPullRequestInfo> getPullRequest(String owner, String repoName, Long pullNumber) {
			return GithubAppClient.this.getPullRequest(accessToken, owner, repoName, pullNumber);
		}
	}

	private List<GithubPullRequestSummary> getClosedPullRequests(String accessToken, String owner, String repoName) {
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

	private List<GithubWeeklySummaryData.PullRequest> getWeeklyPullRequests(
		String accessToken,
		GithubRepositoryInfo repository,
		Long authorGithubUserId,
		Instant periodStart,
		Instant periodEnd
	) {
		List<GithubWeeklySummaryData.PullRequest> pullRequests = new ArrayList<>();
		int page = 1;

		while (true) {
			List<GithubPullRequestResponse> response = getPullRequests(accessToken, repository.owner(), repository.repoName(), page);

			response.stream()
				.filter(this::hasGithubUser)
				.filter(pullRequest -> authorGithubUserId.equals(pullRequest.user().id()))
				.filter(pullRequest -> hasActivityInPeriod(pullRequest, periodStart, periodEnd))
				.map(pullRequest -> new GithubWeeklySummaryData.PullRequest(
					pullRequest.id(),
					pullRequest.number(),
					pullRequest.title(),
					pullRequest.body(),
					pullRequest.user().id(),
					pullRequest.user().login(),
					pullRequest.state(),
					pullRequest.createdAt(),
					pullRequest.updatedAt(),
					pullRequest.closedAt(),
					pullRequest.mergedAt(),
					pullRequest.htmlUrl()
				))
				.forEach(pullRequests::add);

			if (response.size() < PULL_REQUEST_PAGE_SIZE || isAllUpdatedBefore(response, periodStart)) {
				return pullRequests;
			}
			page++;
		}
	}

	private List<GithubWeeklySummaryData.Issue> getWeeklyIssues(
		String accessToken,
		GithubRepositoryInfo repository,
		Long authorGithubUserId,
		Instant periodStart,
		Instant periodEnd
	) {
		List<GithubWeeklySummaryData.Issue> issues = new ArrayList<>();
		int page = 1;

		while (true) {
			List<GithubIssueResponse> response = getIssues(accessToken, repository.owner(), repository.repoName(), periodStart, page);

			response.stream()
				.filter(issue -> issue.pullRequest() == null)
				.filter(this::hasGithubUser)
				.filter(issue -> authorGithubUserId.equals(issue.user().id()))
				.filter(issue -> hasActivityInPeriod(issue, periodStart, periodEnd))
				.map(issue -> new GithubWeeklySummaryData.Issue(
					issue.id(),
					issue.number(),
					issue.title(),
					issue.body(),
					issue.user().id(),
					issue.user().login(),
					issue.state(),
					issue.createdAt(),
					issue.updatedAt(),
					issue.closedAt(),
					issue.htmlUrl()
				))
				.forEach(issues::add);

			if (response.size() < ISSUE_PAGE_SIZE || isAllUpdatedBeforeIssues(response, periodStart)) {
				return issues;
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
		return createPullRequestSyncSession(installationId).getPullRequest(owner, repoName, pullNumber);
	}

	private Optional<GithubPullRequestInfo> getPullRequest(
		String accessToken,
		String owner,
		String repoName,
		Long pullNumber
	) {
		GithubPullRequestResponse response = requestPullRequest(accessToken, owner, repoName, pullNumber);
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

	private List<GithubPullRequestResponse> getPullRequests(
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
					.queryParam("state", "all")
					.queryParam("sort", "updated")
					.queryParam("direction", "desc")
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

	private List<GithubIssueResponse> getIssues(
		String accessToken,
		String owner,
		String repoName,
		Instant since,
		int page
	) {
		try {
			List<GithubIssueResponse> response = restClient.get()
				.uri(UriComponentsBuilder
					.fromUriString(githubAppProperties.apiBaseUrl())
					.pathSegment("repos", owner, repoName, "issues")
					.queryParam("state", "all")
					.queryParam("since", since.toString())
					.queryParam("sort", "updated")
					.queryParam("direction", "desc")
					.queryParam("per_page", ISSUE_PAGE_SIZE)
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

	private GithubPullRequestResponse requestPullRequest(
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

	private boolean hasGithubUser(GithubIssueResponse issue) {
		return issue.user() != null
			&& issue.user().id() != null
			&& issue.user().login() != null
			&& !issue.user().login().isBlank();
	}

	private boolean hasActivityInPeriod(GithubPullRequestResponse pullRequest, Instant periodStart, Instant periodEnd) {
		return isInPeriod(pullRequest.createdAt(), periodStart, periodEnd)
			|| isInPeriod(pullRequest.updatedAt(), periodStart, periodEnd)
			|| isInPeriod(pullRequest.closedAt(), periodStart, periodEnd)
			|| isInPeriod(pullRequest.mergedAt(), periodStart, periodEnd);
	}

	private boolean hasActivityInPeriod(GithubIssueResponse issue, Instant periodStart, Instant periodEnd) {
		return isInPeriod(issue.createdAt(), periodStart, periodEnd)
			|| isInPeriod(issue.updatedAt(), periodStart, periodEnd)
			|| isInPeriod(issue.closedAt(), periodStart, periodEnd);
	}

	private boolean isInPeriod(Instant value, Instant periodStart, Instant periodEnd) {
		return value != null && !value.isBefore(periodStart) && value.isBefore(periodEnd);
	}

	private boolean isAllUpdatedBefore(List<GithubPullRequestResponse> pullRequests, Instant periodStart) {
		return !pullRequests.isEmpty()
			&& pullRequests.stream()
				.allMatch(pullRequest -> pullRequest.updatedAt() != null && pullRequest.updatedAt().isBefore(periodStart));
	}

	private boolean isAllUpdatedBeforeIssues(List<GithubIssueResponse> issues, Instant periodStart) {
		return !issues.isEmpty()
			&& issues.stream()
				.allMatch(issue -> issue.updatedAt() != null && issue.updatedAt().isBefore(periodStart));
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
		String body,
		@JsonProperty("created_at")
		Instant createdAt,
		@JsonProperty("updated_at")
		Instant updatedAt,
		@JsonProperty("closed_at")
		Instant closedAt,
		@JsonProperty("merged_at")
		Instant mergedAt,
		Integer additions,
		Integer deletions,
		@JsonProperty("changed_files")
		Integer changedFiles,
		@JsonProperty("html_url")
		String htmlUrl
	) {
	}

	private record GithubPullRequestUserResponse(
		Long id,
		String login
	) {
	}

	private record GithubIssueResponse(
		Long id,
		Long number,
		String title,
		String body,
		GithubPullRequestUserResponse user,
		String state,
		@JsonProperty("created_at")
		Instant createdAt,
		@JsonProperty("updated_at")
		Instant updatedAt,
		@JsonProperty("closed_at")
		Instant closedAt,
		@JsonProperty("html_url")
		String htmlUrl,
		@JsonProperty("pull_request")
		GithubIssuePullRequestResponse pullRequest
	) {
	}

	private record GithubIssuePullRequestResponse(
		String url
	) {
	}
}
