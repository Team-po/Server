package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
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
