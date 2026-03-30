package team.po.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import team.po.common.jwt.UserPrincipal;

class LoginUserArgumentResolverTest {

	private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void supportsParameter_returnsTrueForLoginUserInfoAnnotatedWithLoginUser() throws NoSuchMethodException {
		MethodParameter parameter = loginUserInfoParameter();

		assertThat(resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void supportsParameter_returnsFalseWithoutAnnotation() throws NoSuchMethodException {
		MethodParameter parameter = plainStringParameter();

		assertThat(resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void resolveArgument_returnsLoginUserInfoFromSecurityContext() throws Exception {
		MethodParameter parameter = loginUserInfoParameter();
		UserPrincipal principal = new UserPrincipal(1L, "test@email.com");
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);

		Object resolved = resolver.resolveArgument(
			parameter,
			null,
			new ServletWebRequest(new org.springframework.mock.web.MockHttpServletRequest()),
			null
		);

		assertThat(resolved).isEqualTo(new LoginUserInfo(1L, "test@email.com"));
	}

	@Test
	void resolveArgument_throwsWhenAuthenticationIsMissing() throws Exception {
		MethodParameter parameter = loginUserInfoParameter();

		assertThatThrownBy(() -> resolver.resolveArgument(
			parameter,
			null,
			new ServletWebRequest(new org.springframework.mock.web.MockHttpServletRequest()),
			null
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No authenticated user found.");
	}

	private MethodParameter loginUserInfoParameter() throws NoSuchMethodException {
		Method method = SampleController.class.getDeclaredMethod("secured", LoginUserInfo.class);
		return new MethodParameter(method, 0);
	}

	private MethodParameter plainStringParameter() throws NoSuchMethodException {
		Method method = SampleController.class.getDeclaredMethod("plain", String.class);
		return new MethodParameter(method, 0);
	}

	@SuppressWarnings("unused")
	private static class SampleController {
		void secured(@LoginUser LoginUserInfo loginUserInfo) {
		}

		void plain(String value) {
		}
	}
}
