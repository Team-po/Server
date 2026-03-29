package team.po.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private FilterChain filterChain;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void doFilter_skipsAccessTokenValidationForRefreshTokenPath() throws ServletException, IOException {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/refresh-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.addHeader("Authorization", "Bearer expired-access-token");

		filter.doFilter(request, response, filterChain);

		verify(jwtTokenProvider, never()).validateAccessToken("expired-access-token");
		verify(filterChain).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void doFilter_returnsUnauthorizedWhenAccessTokenIsInvalid() throws ServletException, IOException {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.addHeader("Authorization", "Bearer expired-access-token");
		when(jwtTokenProvider.validateAccessToken("expired-access-token")).thenReturn(false);

		filter.doFilter(request, response, filterChain);

		verify(jwtTokenProvider).validateAccessToken("expired-access-token");
		verify(filterChain, never()).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"Invalid or expired access token.\"}");
	}

	@Test
	void doFilter_setsAuthenticationWhenAccessTokenIsValid() throws ServletException, IOException {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.addHeader("Authorization", "Bearer valid-access-token");
		UserPrincipal principal = new UserPrincipal(1L, "test@email.com");
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, "valid-access-token", principal.getAuthorities());

		when(jwtTokenProvider.validateAccessToken("valid-access-token")).thenReturn(true);
		when(jwtTokenProvider.getAuthentication("valid-access-token")).thenReturn(authentication);

		filter.doFilter(request, response, filterChain);

		verify(jwtTokenProvider).validateAccessToken("valid-access-token");
		verify(jwtTokenProvider).getAuthentication("valid-access-token");
		verify(filterChain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
	}
}
