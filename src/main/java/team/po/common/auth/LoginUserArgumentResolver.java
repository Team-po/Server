package team.po.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import lombok.RequiredArgsConstructor;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {
	private final UserRepository userRepository;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginUser.class)
			&& Users.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new ApplicationException(ErrorCode.NO_AUTHENTICATED_USER);
		}

		Object principal = authentication.getPrincipal();
		if (!(principal instanceof UserPrincipal userPrincipal)) {
			throw new ApplicationException(ErrorCode.INVALID_SECURITY_CONTEXT);
		}

		return userRepository.findByIdAndDeletedAtIsNull(userPrincipal.id())
			.orElseThrow(() -> new ApplicationException(ErrorCode.UNEXISTED_USER));
	}
}
