package team.po.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

	@Test
	void corsConfigurationSource_allowsLocalhostAndLoopbackViteOrigins() {
		SecurityConfig securityConfig = new SecurityConfig();

		CorsConfiguration configuration = securityConfig
			.corsConfigurationSource()
			.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/project-groups/me"));

		assertThat(configuration).isNotNull();
		assertThat(configuration.getAllowedOrigins())
			.contains("http://localhost:5173", "http://127.0.0.1:5173");
	}
}
