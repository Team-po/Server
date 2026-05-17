package team.po.config;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import team.po.common.jwt.JwtAuthenticationFilter;
import team.po.common.jwt.JwtTokenProvider;
import team.po.exception.ErrorCode;
import team.po.exception.ExceptionResponse;
import team.po.feature.user.oauth.GithubOAuthSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Bean
	public SecurityFilterChain filterChain(
		HttpSecurity http,
		JwtTokenProvider jwtTokenProvider,
		GithubOAuthSuccessHandler githubOAuthSuccessHandler
	) throws Exception {
		http
			.httpBasic(AbstractHttpConfigurer::disable)
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception.authenticationEntryPoint(this::writeUnauthorizedResponse))
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(HttpMethod.POST, "/api/users/sign-up").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/signup/email").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/signup/number-validation").permitAll()
					.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/users/sign-in").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/users/refresh-token").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/oauth/github/token").permitAll()
					.requestMatchers("/oauth2/authorization/github", "/api/auth/github/callback").permitAll()
					.requestMatchers(HttpMethod.GET, "/api/users/check-email").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/users/profile-image/upload-url").permitAll()
					.requestMatchers("/error").permitAll()
					.anyRequest().authenticated()
				)
			.oauth2Login(oauth2 -> oauth2
						.redirectionEndpoint(redirection -> redirection.baseUri("/api/auth/github/callback"))
						.successHandler(githubOAuthSuccessHandler)
					)
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, objectMapper), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(
			"http://localhost:5173",
			"http://localhost:3000",
			"https://team-po.cloud",
			"https://www.team-po.cloud"
		));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
		configuration.setExposedHeaders(List.of("Authorization"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
		throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	private void writeUnauthorizedResponse(
		jakarta.servlet.http.HttpServletRequest request,
		HttpServletResponse response,
		org.springframework.security.core.AuthenticationException exception
	) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ExceptionResponse.from(ErrorCode.NO_AUTHENTICATED_USER));
	}
}
