package team.po.common.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import team.po.common.jwt.UserPrincipal;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

public final class SecurityUtil {

	private SecurityUtil() {
	}

	public static String getCurrentEmail() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new ApplicationException(ErrorCode.NO_AUTHENTICATED_USER);
		}

		Object principal = authentication.getPrincipal();

		if (principal instanceof UserDetails userDetails) {
			return userDetails.getUsername();
		}

		if (principal instanceof String principalValue) {
			return principalValue;
		}

		throw new ApplicationException(ErrorCode.INVALID_SECURITY_CONTEXT);
	}

	public static Long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new ApplicationException(ErrorCode.NO_AUTHENTICATED_USER);
		}

		Object principal = authentication.getPrincipal();

		if (principal instanceof UserPrincipal userPrincipal) {
			return userPrincipal.id();
		}

		throw new ApplicationException(ErrorCode.INVALID_SECURITY_CONTEXT);
	}
}
