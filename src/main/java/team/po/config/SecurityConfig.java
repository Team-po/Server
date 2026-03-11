package team.po.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import team.po.common.jwt.JwtAuthenticationFilter;
import team.po.common.jwt.JwtTokenProvider;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(
		HttpSecurity http,
		JwtTokenProvider jwtTokenProvider
	) throws Exception {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		PathPatternRequestMatcher[] permitAllList = {
			path.matcher("/"),
		};

		http
			.httpBasic(AbstractHttpConfigurer::disable)
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception.authenticationEntryPoint(this::writeUnauthorizedResponse))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(permitAllList).permitAll()
				.requestMatchers(HttpMethod.DELETE, "/user").hasRole("ADMIN")
				.requestMatchers("/members/role").hasRole("USER")
				.anyRequest().authenticated()
			)
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private void writeUnauthorizedResponse(
		jakarta.servlet.http.HttpServletRequest request,
		HttpServletResponse response,
		org.springframework.security.core.AuthenticationException exception
	) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"message\":\"Authentication is required.\"}");
	}
}
