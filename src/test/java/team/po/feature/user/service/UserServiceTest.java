package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.ApplicationException;
import team.po.feature.user.domain.GithubAccount;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
import team.po.feature.user.dto.RefreshTokenRequest;
import team.po.feature.user.dto.RefreshTokenResponse;
import team.po.feature.user.dto.SignInRequest;
import team.po.feature.user.dto.SignInResponse;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.dto.ValidateDeleteUserEmailRequest;
import team.po.feature.user.repository.GithubAccountRepository;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private GithubAccountRepository githubAccountRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private ProfileImageRedisService profileImageRedisService;

	@Mock
	private EmailService emailService;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(userService, "s3Endpoint", "https://storage.hwangdo.kr");
		ReflectionTestUtils.setField(userService, "bucket", "team-po");
		ReflectionTestUtils.setField(userService, "region", "ap-northeast-2");
	}

	@Test
	void signUp_savesUserWithNormalizedEmailAndEncodedPassword() {
		SignUpRequest request = new SignUpRequest(" Test@Email.com ", "password123", "tester", 5, "images/sign-up/test.png");
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

		userService.signUp(request);

		ArgumentCaptor<Users> usersCaptor = ArgumentCaptor.forClass(Users.class);
		verify(userRepository).save(usersCaptor.capture());

		Users savedUser = usersCaptor.getValue();
		assertThat(savedUser.getEmail()).isEqualTo("test@email.com");
		assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
		assertThat(savedUser.getNickname()).isEqualTo("tester");
		assertThat(savedUser.getProfileImage()).isEqualTo("images/sign-up/test.png");
		assertThat(savedUser.getDescription()).isNull();
		assertThat(savedUser.getTemperature()).isEqualTo(50);
		assertThat(savedUser.getLevel()).isEqualTo(5);
		assertThat(savedUser.isGithubLogin()).isFalse();
		verify(profileImageRedisService).consumeSignUpTicket("images/sign-up/test.png");
		verify(emailService).consumeVerifiedSignUpEmail("test@email.com");
	}

	@Test
	void signUp_throwsWhenProfileImageKeyWasNotIssued() {
		SignUpRequest request = new SignUpRequest("test@email.com", "password123", "tester", 5, "images/sign-up/test.png");
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		org.mockito.Mockito.doThrow(new ApplicationException(team.po.exception.ErrorCode.INVALID_PROFILE_IMAGE_KEY))
			.when(profileImageRedisService).consumeSignUpTicket("images/sign-up/test.png");

		assertThatThrownBy(() -> userService.signUp(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("발급되지 않았거나 만료된 프로필 이미지 키입니다.");

		verify(passwordEncoder, never()).encode(any());
		verify(emailService, never()).consumeVerifiedSignUpEmail(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void signUp_throwsWhenEmailWasNotVerified() {
		SignUpRequest request = new SignUpRequest("test@email.com", "password123", "tester", 3, null);
		when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
		doThrow(new ApplicationException(team.po.exception.ErrorCode.EMAIL_NOT_VERIFIED))
			.when(emailService).consumeVerifiedSignUpEmail("test@email.com");

		assertThatThrownBy(() -> userService.signUp(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("이메일 인증이 필요합니다.");

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void signUp_throwsWhenEmailAlreadyExists() {
		SignUpRequest request = new SignUpRequest("test@email.com", "password123", "tester", 3, null);
		when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.signUp(request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("중복된 이메일이 존재합니다.");

		verify(emailService, never()).consumeVerifiedSignUpEmail(any());
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
			.isInstanceOf(ApplicationException.class)
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

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(
			authentication);
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
			.isInstanceOf(ApplicationException.class)
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
			.isInstanceOf(ApplicationException.class)
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
			.isInstanceOf(ApplicationException.class)
			.hasMessage("유효하지 않은 리프레시 토큰입니다.");

		verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
		verify(jwtTokenProvider, never()).getExpiration(any());
	}

	@Test
	void getMyProfile_returnsProfileFromLoginUser() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		loginUser.editProfileImage("profile.png");
		loginUser.editDescription("hello");
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		GetProfileResponse response = userService.getMyProfile(loginUser);

		assertThat(response.email()).isEqualTo("test@email.com");
		assertThat(response.profileImage()).isEqualTo("https://storage.hwangdo.kr/team-po/profile.png");
		assertThat(response.description()).isEqualTo("hello");
		assertThat(response.nickname()).isEqualTo("tester");
		assertThat(response.temperature()).isEqualTo(50);
		assertThat(response.level()).isEqualTo(3);
		assertThat(response.isGithubLogin()).isFalse();
		assertThat(response.isGithubLinked()).isFalse();
		assertThat(response.githubUsername()).isNull();
	}

	@Test
	void getMyProfile_returnsGithubStatusWhenGithubAccountIsLinked() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		GithubAccount githubAccount = GithubAccount.builder()
			.user(loginUser)
			.githubUserId(123L)
			.githubUsername("octocat")
			.build();
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(githubAccount));

		GetProfileResponse response = userService.getMyProfile(loginUser);

		assertThat(response.isGithubLogin()).isFalse();
		assertThat(response.isGithubLinked()).isTrue();
		assertThat(response.githubUsername()).isEqualTo("octocat");
	}

	@Test
	void getMyProfile_returnsAwsS3ProfileImageUrlWhenEndpointIsBlank() {
		ReflectionTestUtils.setField(userService, "s3Endpoint", "");
		Users loginUser = authenticatedUser(1L, "test@email.com");
		loginUser.editProfileImage("images/users/1/profile.png");
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		GetProfileResponse response = userService.getMyProfile(loginUser);

		assertThat(response.profileImage())
			.isEqualTo("https://team-po.s3.ap-northeast-2.amazonaws.com/images/users/1/profile.png");
	}

	@Test
	void editMyProfile_updatesProfileFieldsOnManagedUser() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		EditProfileRequest request = new EditProfileRequest("updated-description", "updated-nickname", 4, "images/users/1/new.png");
		loginUser.editProfileImage("profile.png");
		loginUser.editDescription("old-description");
		loginUser.editNickname("old-nickname");

		userService.editMyProfile(loginUser, request);

		assertThat(loginUser.getDescription()).isEqualTo("updated-description");
		assertThat(loginUser.getNickname()).isEqualTo("updated-nickname");
		assertThat(loginUser.getLevel()).isEqualTo(4);
		assertThat(loginUser.getProfileImage()).isEqualTo("images/users/1/new.png");
		verify(profileImageRedisService).consumeProfileUpdateTicket(1L, "images/users/1/new.png");
	}

	@Test
	void editMyProfile_throwsWhenProfileImageKeyWasNotIssuedForLoginUser() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		EditProfileRequest request = new EditProfileRequest("updated-description", "updated-nickname", 4, "images/users/1/new.png");
		loginUser.editProfileImage("profile.png");
		org.mockito.Mockito.doThrow(new ApplicationException(team.po.exception.ErrorCode.INVALID_PROFILE_IMAGE_KEY))
			.when(profileImageRedisService).consumeProfileUpdateTicket(1L, "images/users/1/new.png");

		assertThatThrownBy(() -> userService.editMyProfile(loginUser, request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("발급되지 않았거나 만료된 프로필 이미지 키입니다.");

		assertThat(loginUser.getProfileImage()).isEqualTo("profile.png");
	}

	@Test
	void editPassword_updatesPasswordOnManagedUserWhenCurrentPasswordMatches() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		EditPasswordRequest request = new EditPasswordRequest("current-password", "new-password123");
		managedUser.editPassword("encoded-current-password");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));
		when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);
		when(passwordEncoder.encode("new-password123")).thenReturn("encoded-new-password");

		userService.editPassword(loginUser, request);

		assertThat(managedUser.getPassword()).isEqualTo("encoded-new-password");
		verify(passwordEncoder).encode("new-password123");
		verify(userRepository).findByIdAndDeletedAtIsNull(1L);
		verify(jwtTokenProvider).deleteRefreshToken("test@email.com");
	}

	@Test
	void editPassword_throwsWhenCurrentPasswordDoesNotMatch() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		EditPasswordRequest request = new EditPasswordRequest("wrong-password", "new-password123");
		managedUser.editPassword("encoded-current-password");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));
		when(passwordEncoder.matches("wrong-password", "encoded-current-password")).thenReturn(false);

		assertThatThrownBy(() -> userService.editPassword(loginUser, request))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("현재 비밀번호와 동일하지 않습니다.");

		verify(passwordEncoder, never()).encode(any());
		verify(jwtTokenProvider, never()).deleteRefreshToken(any());
	}

	@Test
	void sendDeleteUserEmail_sendsEmailToManagedUserEmail() {
		Users loginUser = authenticatedUser(1L, "login@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));

		userService.sendDeleteUserEmail(loginUser);

		verify(emailService).sendDeleteUserEmail("test@email.com");
	}

	@Test
	void validateDeleteUserEmail_validatesManagedUserEmail() {
		Users loginUser = authenticatedUser(1L, "login@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		ValidateDeleteUserEmailRequest request = new ValidateDeleteUserEmailRequest(123456);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));

		userService.validateDeleteUserEmail(loginUser, request);

		verify(emailService).validateDeleteUserAuthNumber("test@email.com", 123456);
	}

	@Test
	void deleteUser_softDeletesManagedUserAndDeletesRefreshToken() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		GithubAccount githubAccount = GithubAccount.builder()
			.user(managedUser)
			.githubUserId(123L)
			.githubUsername("octocat")
			.build();
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(githubAccount));

		userService.deleteUser(loginUser);

		assertThat(managedUser.getDeletedAt()).isNotNull();
		assertThat(managedUser.getEmail()).startsWith("deleted__1__");
		assertThat(managedUser.getEmail()).doesNotContain("test@email.com");
		assertThat(managedUser.getEmail().length()).isLessThanOrEqualTo(255);
		assertThat(githubAccount.getDeletedAt()).isEqualTo(managedUser.getDeletedAt());
		verify(userRepository).findByIdAndDeletedAtIsNull(1L);
		verify(githubAccountRepository).findByUserIdAndDeletedAtIsNull(1L);
		InOrder inOrder = inOrder(emailService, userRepository, jwtTokenProvider);
		inOrder.verify(emailService).validateVerifiedDeleteUserEmail("test@email.com");
		inOrder.verify(userRepository).flush();
		inOrder.verify(jwtTokenProvider).deleteRefreshToken("test@email.com");
		inOrder.verify(emailService).consumeVerifiedDeleteUserEmail("test@email.com");
	}

	@Test
	void deleteUser_createsBoundedDeletedEmailForLongOriginalEmail() {
		Users loginUser = authenticatedUser(1L, "very-long@email.com");
		Users managedUser = authenticatedUser(1L, "very-long@email.com");
		String longLocalPart = "a".repeat(120);
		String longDomainPart = "b".repeat(120);
		String originalEmail = longLocalPart + "@" + longDomainPart + ".com";
		ReflectionTestUtils.setField(managedUser, "email", originalEmail);
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));

		userService.deleteUser(loginUser);

		assertThat(managedUser.getEmail().length()).isLessThanOrEqualTo(255);
		assertThat(managedUser.getEmail()).startsWith("deleted__1__");
		assertThat(managedUser.getEmail()).doesNotContain(originalEmail);
		verify(jwtTokenProvider).deleteRefreshToken(originalEmail);
	}

	@Test
	void deleteUser_throwsWhenEmailWasNotVerified() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));
		doThrow(new ApplicationException(team.po.exception.ErrorCode.EMAIL_NOT_VERIFIED))
			.when(emailService).validateVerifiedDeleteUserEmail("test@email.com");

		assertThatThrownBy(() -> userService.deleteUser(loginUser))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("이메일 인증이 필요합니다.");

		assertThat(managedUser.getDeletedAt()).isNull();
		verify(userRepository, never()).flush();
		verify(jwtTokenProvider, never()).deleteRefreshToken(any());
		verify(emailService, never()).consumeVerifiedDeleteUserEmail(any());
	}

	@Test
	void deleteUser_doesNotConsumeVerifiedEmailWhenSoftDeleteFlushFails() {
		Users loginUser = authenticatedUser(1L, "test@email.com");
		Users managedUser = authenticatedUser(1L, "test@email.com");
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(managedUser));
		doThrow(new DataIntegrityViolationException("failed"))
			.when(userRepository).flush();

		assertThatThrownBy(() -> userService.deleteUser(loginUser))
			.isInstanceOf(DataIntegrityViolationException.class);

		verify(emailService).validateVerifiedDeleteUserEmail("test@email.com");
		verify(emailService, never()).consumeVerifiedDeleteUserEmail(any());
		verify(jwtTokenProvider, never()).deleteRefreshToken(any());
	}

	private Users authenticatedUser(Long id, String email) {
		Users user = Users.builder()
			.email(email)
			.password("encoded-password")
			.nickname("tester")
			.temperature(50)
			.level(3)
			.build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
