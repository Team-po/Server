package team.po.feature.user.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.GithubLinkStartResponse;
import team.po.feature.user.dto.OAuthAuthorizationCodeRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.service.GithubOAuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oauth/github")
public class OAuthController {
	private final GithubOAuthService githubOAuthService;

	@Operation(summary = "GitHub 계정 연동 API")
	@PostMapping("/link-requests")
	public ResponseEntity<GithubLinkStartResponse> startGithubAccountLink(
		@Parameter(hidden = true) @LoginUser Users user
	) {
		String linkCode = githubOAuthService.createGithubLinkCode(user);
		String authorizationUrl = UriComponentsBuilder.fromPath("/oauth2/authorization/github")
			.queryParam("linkCode", linkCode)
			.build()
			.toUriString();
		return ResponseEntity.ok(new GithubLinkStartResponse(authorizationUrl));
	}

	@Operation(summary = "GitHub 계정 연동 해제 API")
	@DeleteMapping("/account")
	public ResponseEntity<Void> unlinkGithubAccount(@Parameter(hidden = true) @LoginUser Users user) {
		githubOAuthService.unlinkGithubAccount(user);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "GitHub OAuth 인가 코드 교환 API")
	@PostMapping("/token")
	public ResponseEntity<SignInResponse> exchangeAuthorizationCode(
		@Valid @RequestBody OAuthAuthorizationCodeRequest request
	) {
		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);
		return ResponseEntity.ok(response);
	}
}
