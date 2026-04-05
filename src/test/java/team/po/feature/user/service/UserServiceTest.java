package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

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
import org.springframework.test.util.ReflectionTestUtils;

import team.po.common.auth.LoginUserInfo;
import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.feature.user.dto.DeleteUserRequest;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.RefreshTokenRequest;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.exception.InvalidPasswordException;
import team.po.feature.user.exception.InvalidTokenException;
import team.po.feature.user.exception.UserNotFoundException;
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

	@InjectMocks
	private UserService userService;

	@Test
	void signUp_savesUserWithNormalizedEmailAndEncodedPassword() {
		SignUpRequest request = new SignUpRequest(" Test@Email.com ", "password123", "tester", 5);
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
		assertThat(savedUser.getLevel()).isEqualTo(5);
	}

	@Test
	void signUp_throwsWhenEmailAlreadyExists() {
		SignUpRequest request = new SignUpRequest("test@email.com", "password123", "tester", 3);
		when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.signUp(request, null))
			.isInstanceOf(DuplicatedEmailException.class);

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void checkEmailDuplication_passesWhenEmailDoesNotExist() {
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);

		userService.checkEmailDuplication(" Test@Email.com ");

		verify(userRepository).existsByEmail("test@email.com");
	}

	@Test
	void checkEmailDuplication_throwsWhenEmailAlreadyExists() {
		when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.checkEmailDuplication(" Test@Email.com "))
			.isInstanceOf(DuplicatedEmailException.class)
			.hasMessage("중복된 이메일이 존재합니다.");

		verify(userRepository).existsByEmail("test@email.com");
	}

	@Test
	void signIn_returnsTokensWhenAuthenticationSucceeds() {
		SignInRequest request = new SignInRequest(" Test@Email.com ", "password123");
		UserPrincipal principal = new UserPrincipal(1L, "test@email.com", "encoded-password");
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		Instant expiresAt = Instant.parse("2026-03-16T11:30:00Z");

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
		when(jwtTokenProvider.generateToken(1L, "test@email.com"))
			.thenReturn(new JwtToken("Bearer", "access-token", "refresh-token", expiresAt));

		SignInResponse response = userService.signIn(request);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.expiresAt()).isEqualTo(expiresAt);

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

	@Test
	void refreshToken_returnsNewAccessTokenWhenRefreshTokenIsValid() {
		RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		Instant accessTokenExpiresAt = Instant.parse("2026-03-16T12:00:00Z");

		when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
		when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
		when(jwtTokenProvider.getEmail("refresh-token")).thenReturn("test@email.com");
		when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
		when(jwtTokenProvider.isRefreshTokenMatched("test@email.com", "refresh-token")).thenReturn(true);
		when(jwtTokenProvider.generateAccessToken(1L, "test@email.com")).thenReturn("new-access-token");
		when(jwtTokenProvider.getExpiration("new-access-token")).thenReturn(accessTokenExpiresAt);

		RefreshTokenResponse response = userService.refreshToken(request);

		assertThat(response.accessToken()).isEqualTo("new-access-token");
		assertThat(response.expiresAt()).isEqualTo(accessTokenExpiresAt);
	}

	@Test
	void refreshToken_throwsWhenRefreshTokenIsInvalid() {
		RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");
		when(jwtTokenProvider.validateRefreshToken("invalid-refresh-token")).thenReturn(false);

		assertThatThrownBy(() -> userService.refreshToken(request))
			.isInstanceOf(InvalidTokenException.class)
			.hasMessage("유효하지 않은 리프레시 토큰입니다.");
	}

	@Test
	void refreshToken_throwsWhenUserDoesNotExist() {
		RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
		when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
		when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
		when(jwtTokenProvider.getEmail("refresh-token")).thenReturn("test@email.com");
		when(userRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.refreshToken(request))
			.isInstanceOf(InvalidTokenException.class)
			.hasMessage("존재하지 않는 유저의 리프레시 토큰입니다.");

		verify(jwtTokenProvider, never()).isRefreshTokenMatched(any(), any());
		verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
	}

	@Test
	void refreshToken_throwsWhenRefreshTokenDoesNotMatchStoredToken() {
		RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();

		when(jwtTokenProvider.validateRefreshToken("refresh-token")).thenReturn(true);
		when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
		when(jwtTokenProvider.getEmail("refresh-token")).thenReturn("test@email.com");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(jwtTokenProvider.isRefreshTokenMatched("test@email.com", "refresh-token")).thenReturn(false);

		assertThatThrownBy(() -> userService.refreshToken(request))
			.isInstanceOf(InvalidTokenException.class)
			.hasMessage("유효하지 않은 리프레시 토큰입니다.");

		verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
		verify(jwtTokenProvider, never()).getExpiration(any());
	}

	@Test
	void getMyProfile_returnsProfileWhenUserExists() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.profileImage("profile.png")
			.description("hello")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

		GetProfileResponse response = userService.getMyProfile(loginUser);

		assertThat(response.email()).isEqualTo("test@email.com");
		assertThat(response.profileImage()).isEqualTo("profile.png");
		assertThat(response.description()).isEqualTo("hello");
		assertThat(response.nickname()).isEqualTo("tester");
		assertThat(response.temperature()).isEqualTo(50);
		assertThat(response.level()).isEqualTo(3);
	}

	@Test
	void getMyProfile_throwsWhenUserDoesNotExist() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getMyProfile(loginUser))
			.isInstanceOf(UserNotFoundException.class)
			.hasMessage("존재하지 않은 유저입니다.");
	}

	@Test
	void getMyProfile_throwsWhenUserIsSoftDeleted() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "deletedAt", Instant.parse("2026-03-30T00:00:00Z"));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getMyProfile(loginUser))
			.isInstanceOf(UserNotFoundException.class)
			.hasMessage("존재하지 않은 유저입니다.");
	}

	@Test
	void editMyProfile_updatesProfileFields() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		EditProfileRequest request = new EditProfileRequest("updated-description", "updated-nickname", 4);
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.profileImage("profile.png")
			.description("old-description")
			.nickname("old-nickname")
			.temperature(50)
			.level(3)
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

		userService.editMyProfile(loginUser, null, request);

		assertThat(user.getDescription()).isEqualTo("updated-description");
		assertThat(user.getNickname()).isEqualTo("updated-nickname");
		assertThat(user.getLevel()).isEqualTo(4);
	}

	@Test
	void editMyProfile_throwsWhenUserDoesNotExist() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		EditProfileRequest request = new EditProfileRequest("updated-description", "updated-nickname", 4);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.editMyProfile(loginUser, null, request))
			.isInstanceOf(UserNotFoundException.class)
			.hasMessage("존재하지 않은 유저입니다.");

	}

	@Test
	void editMyProfile_throwsWhenUserIsSoftDeleted() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		EditProfileRequest request = new EditProfileRequest("updated-description", "updated-nickname", 4);
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-password")
			.nickname("old-nickname")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "deletedAt", Instant.parse("2026-03-30T00:00:00Z"));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.editMyProfile(loginUser, null, request))
			.isInstanceOf(UserNotFoundException.class)
			.hasMessage("존재하지 않은 유저입니다.");

	}

	@Test
	void editPassword_updatesPasswordWhenCurrentPasswordMatches() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		EditPasswordRequest request = new EditPasswordRequest("current-password", "new-password123");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-current-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);
		when(passwordEncoder.encode("new-password123")).thenReturn("encoded-new-password");

		userService.editPassword(loginUser, request);

		assertThat(user.getPassword()).isEqualTo("encoded-new-password");
		verify(passwordEncoder).encode("new-password123");
		verify(jwtTokenProvider).deleteRefreshToken("test@email.com");
	}

	@Test
	void editPassword_throwsWhenCurrentPasswordDoesNotMatch() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		EditPasswordRequest request = new EditPasswordRequest("wrong-password", "new-password123");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-current-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "encoded-current-password")).thenReturn(false);

		assertThatThrownBy(() -> userService.editPassword(loginUser, request))
			.isInstanceOf(InvalidPasswordException.class)
			.hasMessage("현재 비밀번호와 동일하지 않습니다.");

		verify(passwordEncoder, never()).encode(any());
		verify(jwtTokenProvider, never()).deleteRefreshToken(any());
	}

	@Test
	void deleteUser_softDeletesUserAndDeletesRefreshToken() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		DeleteUserRequest request = new DeleteUserRequest("current-password");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-current-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);

		userService.deleteUser(loginUser, request);

		assertThat(user.getDeletedAt()).isNotNull();
		assertThat(user.getEmail()).startsWith("deleted__1__");
		assertThat(user.getEmail()).doesNotContain("test@email.com");
		assertThat(user.getEmail().length()).isLessThanOrEqualTo(255);
		verify(jwtTokenProvider).deleteRefreshToken("test@email.com");
	}

	@Test
	void deleteUser_createsBoundedDeletedEmailForLongOriginalEmail() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "very-long@email.com");
		String longLocalPart = "a".repeat(120);
		String longDomainPart = "b".repeat(120);
		String originalEmail = longLocalPart + "@" + longDomainPart + ".com";
		DeleteUserRequest request = new DeleteUserRequest("current-password");
		Users user = Users.builder()
			.email(originalEmail)
			.password("encoded-current-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);

		userService.deleteUser(loginUser, request);

		assertThat(user.getEmail().length()).isLessThanOrEqualTo(255);
		assertThat(user.getEmail()).startsWith("deleted__1__");
		assertThat(user.getEmail()).doesNotContain(originalEmail);
		verify(jwtTokenProvider).deleteRefreshToken(originalEmail);
	}

	@Test
	void deleteUser_throwsWhenPasswordDoesNotMatch() {
		LoginUserInfo loginUser = new LoginUserInfo(1L, "test@email.com");
		DeleteUserRequest request = new DeleteUserRequest("wrong-password");
		Users user = Users.builder()
			.email("test@email.com")
			.password("encoded-current-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "encoded-current-password")).thenReturn(false);

		assertThatThrownBy(() -> userService.deleteUser(loginUser, request))
			.isInstanceOf(InvalidPasswordException.class)
			.hasMessage("현재 비밀번호와 동일하지 않습니다.");

		verify(jwtTokenProvider, never()).deleteRefreshToken(any());
	}
}
