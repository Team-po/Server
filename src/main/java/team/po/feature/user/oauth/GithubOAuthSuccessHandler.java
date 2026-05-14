package team.po.feature.user.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
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

		OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
		OAuth2User oAuth2User = (OAuth2User)oauthToken.getPrincipal();
		String tokenValue = accessToken.getTokenValue();

		try {
			boolean linked = githubOAuthService.linkGithubAccountIfRequested(
				request.getParameter("state"),
				oAuth2User,
				tokenValue,
				accessToken.getTokenType().getValue(),
				accessToken.getScopes()
			);
			if (linked) {
				redirectGithubLinkResult(response, true, null);
				return;
			}
		} catch (ApplicationException exception) {
			redirectGithubLinkResult(response, false, exception.getCode());
			return;
		}

		GithubAuthorizationCode authorizationCode = githubOAuthService.createGithubAuthorizationCode(oAuth2User, tokenValue);
		String redirectUri = UriComponentsBuilder.fromUriString(successRedirectUri)
			.queryParam("code", authorizationCode.authorizationCode())
			.queryParam("onboardingRequired", authorizationCode.onboardingRequired())
			.build()
			.toUriString();

		response.sendRedirect(redirectUri);
	}

	private void redirectGithubLinkResult(HttpServletResponse response, boolean linked, String errorCode) throws IOException {
		UriComponentsBuilder redirectUriBuilder = UriComponentsBuilder.fromUriString(successRedirectUri)
			.queryParam("githubLinked", linked);

		if (errorCode != null) {
			redirectUriBuilder.queryParam("error", errorCode);
		}

		response.sendRedirect(redirectUriBuilder.build().toUriString());
	}
}
