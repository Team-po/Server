package team.po.common.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtil {

	private SecurityUtil() {
	}

	public static String getCurrentEmail() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new IllegalStateException("No authenticated user found.");
		}

		Object principal = authentication.getPrincipal();

		if (principal instanceof UserDetails userDetails) {
			return userDetails.getUsername();
		}

		if (principal instanceof String principalValue) {
			return principalValue;
		}

		throw new IllegalStateException("Cannot extract current email from authentication.");
	}
}
