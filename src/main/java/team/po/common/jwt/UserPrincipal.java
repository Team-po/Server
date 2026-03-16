package team.po.common.jwt;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import team.po.feature.user.domain.Users;

public record UserPrincipal(Long id, String email, String password) implements UserDetails {

	public UserPrincipal(Long id, String email) {
		this(id, email, "");
	}

	public static UserPrincipal from(Users user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return email;
	}
}
