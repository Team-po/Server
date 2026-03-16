package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import team.po.common.jwt.JwtProperties;
import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private JwtProperties jwtProperties;

	@InjectMocks
	private UserService userService;

	@Test
	void signUp_savesUserWithNormalizedEmailAndEncodedPassword() {
		SignUpRequest request = new SignUpRequest(" Test@Email.com ", "password123", "tester");
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

		userService.signUp(request, null);

		ArgumentCaptor<Users> usersCaptor = ArgumentCaptor.forClass(Users.class);
		verify(userRepository).save(usersCaptor.capture());

		Users savedUser = usersCaptor.getValue();
		assertThat(savedUser.getEmail()).isEqualTo("test@email.com");
		assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
		assertThat(savedUser.getNickname()).isEqualTo("tester");
		assertThat(savedUser.getDescription()).isNull();
		assertThat(savedUser.getTemperature()).isEqualTo(50);
		assertThat(savedUser.getLevel()).isEqualTo(3);
	}

	@Test
	void signUp_throwsWhenEmailAlreadyExists() {
		SignUpRequest request = new SignUpRequest("test@email.com", "password123", "tester");
		when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.signUp(request, null))
			.isInstanceOf(DuplicatedEmailException.class);

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void signIn_returnsTokensWhenAuthenticationSucceeds() {
		SignInRequest request = new SignInRequest(" Test@Email.com ", "password123");
		UserPrincipal principal = new UserPrincipal(1L, "test@email.com", "encoded-password");
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(new JwtToken("Bearer", "access-token", "refresh-token"));
		when(jwtProperties.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(30));

		LocalDateTime before = LocalDateTime.now();
		SignInResponse response = userService.signIn(request);
		LocalDateTime after = LocalDateTime.now();

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.expiresAt()).isAfterOrEqualTo(before.plusMinutes(30));
		assertThat(response.expiresAt()).isBeforeOrEqualTo(after.plusMinutes(30));

		ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor =
			ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
		verify(authenticationManager).authenticate(authenticationCaptor.capture());
		assertThat(authenticationCaptor.getValue().getPrincipal()).isEqualTo("test@email.com");
		assertThat(authenticationCaptor.getValue().getCredentials()).isEqualTo("password123");
	}

	@Test
	void signIn_throwsWhenAuthenticationFails() {
		SignInRequest request = new SignInRequest("test@email.com", "wrong-password");
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
			.thenThrow(new BadCredentialsException("bad credentials"));

		assertThatThrownBy(() -> userService.signIn(request))
			.isInstanceOf(BadCredentialsException.class)
			.hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
	}
}
