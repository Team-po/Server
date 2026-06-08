package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.teamspace.dto.GithubPullRequestInfo;
import team.po.feature.teamspace.dto.GithubRepositoryInfo;
import team.po.feature.teamspace.provider.GithubAppJwtProvider;
import team.po.feature.teamspace.service.GithubAppClient.GithubAppInstallationInfo;

class GithubAppClientTest {

	@Test
	void getInstallation_returnsInstallationInfo() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer github-app-jwt");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			writeResponse(exchange, 200, """
				{
				  "id": 12345,
				  "account": {
				    "id": 98765,
				    "login": "student-team-org",
				    "type": "Organization"
				  }
				}
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			GithubAppInstallationInfo response = client.getInstallation(12345L);

			assertThat(response.installationId()).isEqualTo(12345L);
			assertThat(response.accountId()).isEqualTo(98765L);
			assertThat(response.accountLogin()).isEqualTo("student-team-org");
			assertThat(response.accountType()).isEqualTo("Organization");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getInstallation_throwsBadRequest_whenInstallationIsNotFound() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/404", exchange -> writeResponse(exchange, 404, "{}"));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			assertThatThrownBy(() -> client.getInstallation(404L))
				.isInstanceOf(ApplicationException.class)
				.extracting("code")
				.isEqualTo(ErrorCode.GITHUB_APP_INSTALLATION_NOT_FOUND.getCode());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getInstallation_throwsBadGateway_whenGithubApiFails() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345", exchange -> writeResponse(exchange, 500, "{}"));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			assertThatThrownBy(() -> client.getInstallation(12345L))
				.isInstanceOf(ApplicationException.class)
				.extracting("code")
				.isEqualTo(ErrorCode.GITHUB_API_REQUEST_FAILED.getCode());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void validateOrganizationAdmin_passesWhenMembershipIsActiveAdmin() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/user/memberships/orgs/student-team-org", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer github-user-token");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			writeResponse(exchange, 200, """
				{
				  "state": "active",
				  "role": "admin"
				}
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			client.validateOrganizationAdmin("github-user-token", "student-team-org");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getInstallationRepositories_returnsAccessibleRepositories() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> {
			assertThat(exchange.getRequestMethod()).isEqualTo("POST");
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer github-app-jwt");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			writeResponse(exchange, 201, """
				{
				  "token": "github-installation-token"
				}
				""");
		});
		server.createContext("/installation/repositories", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
				.isEqualTo("Bearer github-installation-token");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			assertThat(exchange.getRequestURI().getQuery()).contains("per_page=100", "page=1");
			writeResponse(exchange, 200, """
				{
				  "repositories": [
				    {
				      "id": 123,
				      "name": "backend",
				      "full_name": "student-team-org/backend",
				      "private": true,
				      "default_branch": "main",
				      "owner": {
				        "login": "student-team-org"
				      }
				    },
				    {
				      "id": 456,
				      "name": "frontend",
				      "full_name": "student-team-org/frontend",
				      "private": false,
				      "default_branch": "develop",
				      "owner": {
				        "login": "student-team-org"
				      }
				    }
				  ]
				}
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			var repositories = client.getInstallationRepositories(12345L);

			assertThat(repositories).hasSize(2);
			assertThat(repositories.get(0).githubRepositoryId()).isEqualTo(123L);
			assertThat(repositories.get(0).owner()).isEqualTo("student-team-org");
			assertThat(repositories.get(0).repoName()).isEqualTo("backend");
			assertThat(repositories.get(0).fullName()).isEqualTo("student-team-org/backend");
			assertThat(repositories.get(0).defaultBranch()).isEqualTo("main");
			assertThat(repositories.get(0).privateRepository()).isTrue();
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getClosedPullRequests_returnsClosedPullRequests() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> {
			assertThat(exchange.getRequestMethod()).isEqualTo("POST");
			assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer github-app-jwt");
			writeResponse(exchange, 201, """
				{
				  "token": "github-installation-token"
				}
				""");
		});
		server.createContext("/repos/student-team-org/backend/pulls", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
				.isEqualTo("Bearer github-installation-token");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			assertThat(exchange.getRequestURI().getQuery()).contains("state=closed", "per_page=100", "page=1");
			writeResponse(exchange, 200, """
				[
				  {
				    "id": 999,
				    "number": 9,
				    "title": "Old PR from deleted user",
				    "state": "closed",
				    "merged_at": "2026-04-01T10:15:30Z",
				    "html_url": "https://github.com/student-team-org/backend/pull/9",
				    "user": null
				  },
				  {
				    "id": 1001,
				    "number": 10,
				    "title": "Add contribution sync",
				    "state": "closed",
				    "merged_at": "2026-05-01T10:15:30Z",
				    "html_url": "https://github.com/student-team-org/backend/pull/10",
				    "user": {
				      "id": 501,
				      "login": "dev-a"
				    }
				  }
				]
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			var pullRequests = client.getClosedPullRequests(12345L, "student-team-org", "backend");

			assertThat(pullRequests).hasSize(1);
			assertThat(pullRequests.get(0).githubPullRequestId()).isEqualTo(1001L);
			assertThat(pullRequests.get(0).pullNumber()).isEqualTo(10L);
			assertThat(pullRequests.get(0).title()).isEqualTo("Add contribution sync");
			assertThat(pullRequests.get(0).authorGithubUserId()).isEqualTo(501L);
			assertThat(pullRequests.get(0).authorGithubUsername()).isEqualTo("dev-a");
			assertThat(pullRequests.get(0).state()).isEqualTo("closed");
			assertThat(pullRequests.get(0).htmlUrl())
				.isEqualTo("https://github.com/student-team-org/backend/pull/10");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getPullRequest_returnsPullRequestDetail() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> writeResponse(exchange, 201, """
			{
			  "token": "github-installation-token"
			}
			"""));
		server.createContext("/repos/student-team-org/backend/pulls/10", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
				.isEqualTo("Bearer github-installation-token");
			assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/vnd.github+json");
			assertThat(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
			writeResponse(exchange, 200, """
				{
				  "id": 1001,
				  "number": 10,
				  "title": "Add contribution sync",
				  "state": "closed",
				  "merged_at": "2026-05-01T10:15:30Z",
				  "additions": 120,
				  "deletions": 15,
				  "changed_files": 8,
				  "body": "Closes #10\\nFixes #11 and #12\\nResolves student-team-org/frontend#13\\nCloses #10",
				  "html_url": "https://github.com/student-team-org/backend/pull/10",
				  "user": {
				    "id": 501,
				    "login": "dev-a"
				  }
				}
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			var pullRequest = client.getPullRequest(12345L, "student-team-org", "backend", 10L);

			assertThat(pullRequest).isPresent();
			GithubPullRequestInfo pullRequestInfo = pullRequest.orElseThrow();
			assertThat(pullRequestInfo.githubPullRequestId()).isEqualTo(1001L);
			assertThat(pullRequestInfo.pullNumber()).isEqualTo(10L);
			assertThat(pullRequestInfo.authorGithubUserId()).isEqualTo(501L);
			assertThat(pullRequestInfo.additions()).isEqualTo(120);
			assertThat(pullRequestInfo.deletions()).isEqualTo(15);
			assertThat(pullRequestInfo.changedFiles()).isEqualTo(8);
			assertThat(pullRequestInfo.linkedIssueCount()).isEqualTo(4);
			assertThat(pullRequestInfo.htmlUrl()).isEqualTo("https://github.com/student-team-org/backend/pull/10");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getWeeklySummaryData_returnsAuthorPullRequestsAndIssuesInPeriod() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> writeResponse(exchange, 201, """
			{
			  "token": "github-installation-token"
			}
			"""));
		server.createContext("/repos/student-team-org/backend/pulls", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
				.isEqualTo("Bearer github-installation-token");
			assertThat(exchange.getRequestURI().getQuery())
				.contains("state=all", "sort=updated", "direction=desc", "per_page=100", "page=1");
			writeResponse(exchange, 200, """
				[
				  {
				    "id": 1001,
				    "number": 10,
				    "title": "Add weekly summary",
				    "body": "## 작업 내용\\n- Github 주간 요약 조회",
				    "state": "open",
				    "created_at": "2026-05-26T10:15:30Z",
				    "updated_at": "2026-05-27T10:15:30Z",
				    "closed_at": null,
				    "merged_at": null,
				    "html_url": "https://github.com/student-team-org/backend/pull/10",
				    "user": {
				      "id": 123,
				      "login": "octocat"
				    }
				  },
				  {
				    "id": 1002,
				    "number": 11,
				    "title": "Other user PR",
				    "state": "open",
				    "created_at": "2026-05-27T10:15:30Z",
				    "updated_at": "2026-05-27T10:15:30Z",
				    "html_url": "https://github.com/student-team-org/backend/pull/11",
				    "user": {
				      "id": 999,
				      "login": "other"
				    }
				  }
				]
				""");
		});
		server.createContext("/repos/student-team-org/backend/issues", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
				.isEqualTo("Bearer github-installation-token");
			assertThat(exchange.getRequestURI().getQuery())
				.contains("state=all", "since=2026-05-25", "sort=updated", "direction=desc", "per_page=100", "page=1");
			writeResponse(exchange, 200, """
				[
				  {
				    "id": 2001,
				    "number": 20,
				    "title": "주간 요약 API",
				    "body": "요청자 기준 Github 활동을 요약한다.",
				    "state": "open",
				    "created_at": "2026-05-26T11:00:00Z",
				    "updated_at": "2026-05-26T11:00:00Z",
				    "closed_at": null,
				    "html_url": "https://github.com/student-team-org/backend/issues/20",
				    "user": {
				      "id": 123,
				      "login": "octocat"
				    }
				  },
				  {
				    "id": 2002,
				    "number": 21,
				    "title": "Pull request issue wrapper",
				    "state": "open",
				    "created_at": "2026-05-26T11:00:00Z",
				    "updated_at": "2026-05-26T11:00:00Z",
				    "html_url": "https://github.com/student-team-org/backend/pull/21",
				    "pull_request": {
				      "url": "https://api.github.com/repos/student-team-org/backend/pulls/21"
				    },
				    "user": {
				      "id": 123,
				      "login": "octocat"
				    }
				  }
				]
				""");
		});
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			var data = client.getWeeklySummaryData(
				12345L,
				List.of(new GithubRepositoryInfo(
					100L,
					"student-team-org",
					"backend",
					"student-team-org/backend",
					"main",
					true
				)),
				123L,
				Instant.parse("2026-05-25T00:00:00Z"),
				Instant.parse("2026-06-01T00:00:00Z")
			);

			assertThat(data.pullRequestCount()).isEqualTo(1);
			assertThat(data.issueCount()).isEqualTo(1);
			assertThat(data.repositories()).hasSize(1);
			assertThat(data.repositories().get(0).pullRequests().get(0).pullNumber()).isEqualTo(10L);
			assertThat(data.repositories().get(0).issues().get(0).issueNumber()).isEqualTo(20L);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void pullRequestSyncSession_reusesInstallationAccessToken() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");
		AtomicInteger accessTokenRequestCount = new AtomicInteger();

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> {
			accessTokenRequestCount.incrementAndGet();
			writeResponse(exchange, 201, """
				{
				  "token": "github-installation-token"
				}
				""");
		});
		server.createContext("/repos/student-team-org/backend/pulls", exchange -> writeResponse(exchange, 200, """
			[
			  {
			    "id": 1001,
			    "number": 10,
			    "title": "Add contribution sync",
			    "state": "closed",
			    "merged_at": "2026-05-01T10:15:30Z",
			    "html_url": "https://github.com/student-team-org/backend/pull/10",
			    "user": {
			      "id": 501,
			      "login": "dev-a"
			    }
			  }
			]
			"""));
		server.createContext("/repos/student-team-org/backend/pulls/10", exchange -> writeResponse(exchange, 200, """
			{
			  "id": 1001,
			  "number": 10,
			  "title": "Add contribution sync",
			  "state": "closed",
			  "merged_at": "2026-05-01T10:15:30Z",
			  "additions": 120,
			  "deletions": 15,
			  "changed_files": 8,
			  "body": "Closes #10",
			  "html_url": "https://github.com/student-team-org/backend/pull/10",
			  "user": {
			    "id": 501,
			    "login": "dev-a"
			  }
			}
			"""));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);
			GithubPullRequestSyncSession session = client.createPullRequestSyncSession(12345L);

			assertThat(session.getClosedPullRequests("student-team-org", "backend")).hasSize(1);
			assertThat(session.getPullRequest("student-team-org", "backend", 10L)).isPresent();
			assertThat(accessTokenRequestCount).hasValue(1);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void getPullRequest_returnsEmpty_whenPullRequestUserIsMissing() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);
		when(jwtProvider.generateJwt()).thenReturn("github-app-jwt");

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/app/installations/12345/access_tokens", exchange -> writeResponse(exchange, 201, """
			{
			  "token": "github-installation-token"
			}
			"""));
		server.createContext("/repos/student-team-org/backend/pulls/10", exchange -> writeResponse(exchange, 200, """
			{
			  "id": 1001,
			  "number": 10,
			  "title": "Old PR from deleted user",
			  "state": "closed",
			  "merged_at": "2026-05-01T10:15:30Z",
			  "additions": 120,
			  "deletions": 15,
			  "changed_files": 8,
			  "html_url": "https://github.com/student-team-org/backend/pull/10",
			  "user": null
			}
			"""));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			assertThat(client.getPullRequest(12345L, "student-team-org", "backend", 10L)).isEmpty();
		} finally {
			server.stop(0);
		}
	}

	@Test
	void validateOrganizationAdmin_throwsForbiddenWhenMembershipIsNotAdmin() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/user/memberships/orgs/student-team-org", exchange -> writeResponse(exchange, 200, """
			{
			  "state": "active",
			  "role": "member"
			}
			"""));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			assertThatThrownBy(() -> client.validateOrganizationAdmin("github-user-token", "student-team-org"))
				.isInstanceOf(ApplicationException.class)
				.extracting("code")
				.isEqualTo(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED.getCode());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void validateOrganizationAdmin_throwsForbiddenWhenMembershipIsNotFound() throws Exception {
		GithubAppJwtProvider jwtProvider = Mockito.mock(GithubAppJwtProvider.class);

		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/user/memberships/orgs/student-team-org", exchange -> writeResponse(exchange, 404, "{}"));
		server.start();

		try {
			GithubAppClient client = githubAppClient(server, jwtProvider);

			assertThatThrownBy(() -> client.validateOrganizationAdmin("github-user-token", "student-team-org"))
				.isInstanceOf(ApplicationException.class)
				.extracting("code")
				.isEqualTo(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED.getCode());
		} finally {
			server.stop(0);
		}
	}

	private GithubAppClient githubAppClient(HttpServer server, GithubAppJwtProvider jwtProvider) {
		String apiBaseUrl = "http://localhost:" + server.getAddress().getPort();
		GithubAppProperties properties = new GithubAppProperties(
			12345L,
			"teampo-dev",
			"test-private-key",
			Duration.ofMinutes(5),
			apiBaseUrl
		);
		return new GithubAppClient(RestClient.create(), jwtProvider, properties);
	}

	private void writeResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
		byte[] responseBytes = responseBody.getBytes();
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, responseBytes.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(responseBytes);
		} finally {
			exchange.close();
		}
	}
}
