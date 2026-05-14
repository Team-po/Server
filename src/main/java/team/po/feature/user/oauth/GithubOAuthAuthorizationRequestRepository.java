package team.po.feature.user.oauth;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import team.po.feature.user.service.GithubOAuthService;

@Component
@RequiredArgsConstructor
public class GithubOAuthAuthorizationRequestRepository
	implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
	private static final String LINK_CODE_PARAMETER = "linkCode";

	private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
		new HttpSessionOAuth2AuthorizationRequestRepository();
	private final GithubOAuthService githubOAuthService;

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return delegate.loadAuthorizationRequest(request);
	}

	@Override
	public void saveAuthorizationRequest(
		OAuth2AuthorizationRequest authorizationRequest,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		if (authorizationRequest != null) {
			String linkCode = request.getParameter(LINK_CODE_PARAMETER);
			if (StringUtils.hasText(linkCode)) {
				githubOAuthService.bindGithubLinkState(authorizationRequest.getState(), linkCode);
			}
		}
		delegate.saveAuthorizationRequest(authorizationRequest, request, response);
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(
		HttpServletRequest request,
		HttpServletResponse response
	) {
		return delegate.removeAuthorizationRequest(request, response);
	}
}
