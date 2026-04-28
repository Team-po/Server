package team.po.common.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import team.po.common.jwt.UserPrincipal;
import team.po.exception.ApplicationException;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginUserArgumentResolverTest {

	@Mock
	private UserRepository userRepository;

	private LoginUserArgumentResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new LoginUserArgumentResolver(userRepository);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void supportsParameter_returnsTrueForUsersAnnotatedWithLoginUser() throws NoSuchMethodException {
		MethodParameter parameter = loginUserParameter();

		assertThat(resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void supportsParameter_returnsFalseWithoutAnnotation() throws NoSuchMethodException {
		MethodParameter parameter = plainStringParameter();

		assertThat(resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void resolveArgument_returnsUsersFromSecurityContext() throws Exception {
		MethodParameter parameter = loginUserParameter();
		UserPrincipal principal = new UserPrincipal(1L, "test@email.com");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);

		Object resolved = resolver.resolveArgument(
			parameter,
			null,
			new ServletWebRequest(new org.springframework.mock.web.MockHttpServletRequest()),
			null
		);

		assertThat(resolved).isSameAs(user);
	}

	@Test
	void resolveArgument_throwsWhenAuthenticationIsMissing() throws Exception {
		MethodParameter parameter = loginUserParameter();

		assertThatThrownBy(() -> resolver.resolveArgument(
			parameter,
			null,
			new ServletWebRequest(new org.springframework.mock.web.MockHttpServletRequest()),
			null
		))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("인증된 유저를 찾을 수 없습니다.");
	}

	private MethodParameter loginUserParameter() throws NoSuchMethodException {
		Method method = SampleController.class.getDeclaredMethod("secured", Users.class);
		return new MethodParameter(method, 0);
	}

	private MethodParameter plainStringParameter() throws NoSuchMethodException {
		Method method = SampleController.class.getDeclaredMethod("plain", String.class);
		return new MethodParameter(method, 0);
	}

	@SuppressWarnings("unused")
	private static class SampleController {
		void secured(@LoginUser Users user) {
		}

		void plain(String value) {
		}
	}
}
