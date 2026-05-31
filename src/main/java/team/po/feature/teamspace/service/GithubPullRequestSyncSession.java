package team.po.feature.teamspace.service;

import java.util.List;
import java.util.Optional;

import team.po.feature.teamspace.dto.GithubPullRequestInfo;
import team.po.feature.teamspace.dto.GithubPullRequestSummary;

public interface GithubPullRequestSyncSession {
	List<GithubPullRequestSummary> getClosedPullRequests(String owner, String repoName);

	Optional<GithubPullRequestInfo> getPullRequest(String owner, String repoName, Long pullNumber);
}
