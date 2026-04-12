package team.po.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.exception.InvalidAuthenticationException;
import team.po.feature.user.exception.UserNotFoundException;
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
			throw new InvalidAuthenticationException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.NO_AUTHENTICATED_USER,
				"인증된 유저를 찾을 수 없습니다.");
		}

		Object principal = authentication.getPrincipal();

		if (!(principal instanceof UserPrincipal userPrincipal)) {
			throw new InvalidAuthenticationException(HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.INVALID_SECURITY_CONTEXT, "인증된 사용자 정보를 해석할 수 없습니다.");
		}

		return userRepository.findByIdAndDeletedAtIsNull(userPrincipal.id())
			.orElseThrow(() -> new UserNotFoundException(
				HttpStatus.UNAUTHORIZED,
				ErrorCodeConstants.UNEXISTED_USER,
				"존재하지 않은 유저입니다."
			));
	}
}
