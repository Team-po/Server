package team.po.feature.user.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import team.po.feature.user.dto.GithubAuthorizationCode;
import team.po.feature.user.service.GithubOAuthService;

@Component
@RequiredArgsConstructor
public class GithubOAuthSuccessHandler implements AuthenticationSuccessHandler {
	private final OAuth2AuthorizedClientService authorizedClientService;
	private final GithubOAuthService githubOAuthService;

	@Value("${github.oauth.success-redirect-uri}")
	private String successRedirectUri;

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {
		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken)authentication;
		OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
			oauthToken.getAuthorizedClientRegistrationId(),
			oauthToken.getName()
		);

		String accessToken = authorizedClient.getAccessToken().getTokenValue();
		GithubAuthorizationCode authorizationCode = githubOAuthService.createAuthorizationCode(
			(OAuth2User)oauthToken.getPrincipal(),
			accessToken
		);
		String redirectUri = UriComponentsBuilder.fromUriString(successRedirectUri)
			.queryParam("code", authorizationCode.authorizationCode())
			.queryParam("onboardingRequired", authorizationCode.onboardingRequired())
			.build()
			.toUriString();

		response.sendRedirect(redirectUri);
	}
}
