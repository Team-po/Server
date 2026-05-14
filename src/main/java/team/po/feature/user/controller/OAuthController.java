package team.po.feature.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.feature.user.dto.OAuthAuthorizationCodeRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.service.GithubOAuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/oauth/github")
public class OAuthController {
	private final GithubOAuthService githubOAuthService;

	@Operation(summary = "GitHub OAuth 인가 코드 교환 API")
	@PostMapping("/token")
	public ResponseEntity<SignInResponse> exchangeAuthorizationCode(
		@Valid @RequestBody OAuthAuthorizationCodeRequest request
	) {
		SignInResponse response = githubOAuthService.exchangeGithubAuthorizationCode(request);
		return ResponseEntity.ok(response);
	}
}
