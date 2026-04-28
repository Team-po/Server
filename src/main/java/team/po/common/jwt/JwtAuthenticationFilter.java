package team.po.common.jwt;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.po.exception.ErrorCode;
import team.po.exception.ExceptionResponse;

public class JwtAuthenticationFilter extends GenericFilterBean {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String REFRESH_TOKEN_PATH = "/api/users/refresh-token";

	private final JwtTokenProvider jwtTokenProvider;
	private final ObjectMapper objectMapper;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this(jwtTokenProvider, new ObjectMapper().findAndRegisterModules());
	}

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.objectMapper = objectMapper;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
		throws IOException, ServletException {
		HttpServletRequest httpServletRequest = (HttpServletRequest)request;

		if (isRefreshTokenRequest(httpServletRequest)) {
			chain.doFilter(request, response);
			return;
		}

		String token = resolveToken(httpServletRequest);

		if (StringUtils.hasText(token)) {
			if (!jwtTokenProvider.validateAccessToken(token)) {
				writeUnauthorizedResponse((HttpServletResponse)response);
				return;
			}

			try {
				Authentication authentication = jwtTokenProvider.getAuthentication(token);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (RuntimeException exception) {
				this.writeUnauthorizedResponse((HttpServletResponse)response);
				return;
			}
		}

		chain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(BEARER_PREFIX.length());
		}

		return null;
	}

	private boolean isRefreshTokenRequest(HttpServletRequest request) {
		return REFRESH_TOKEN_PATH.equals(request.getRequestURI());
	}

	private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
		SecurityContextHolder.clearContext();
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
			ExceptionResponse.from(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 액세스 토큰입니다."));
	}
}
