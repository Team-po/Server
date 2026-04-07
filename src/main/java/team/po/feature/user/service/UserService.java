package team.po.feature.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.common.jwt.JwtToken;
import team.po.common.jwt.JwtTokenProvider;
import team.po.common.jwt.UserPrincipal;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.DeleteUserRequest;
import team.po.feature.user.dto.EditPasswordRequest;
import team.po.feature.user.dto.EditProfileRequest;
import team.po.feature.user.dto.GetProfileResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtTokenProvider jwtTokenProvider;

	public void signUp(SignUpRequest signUpRequest, MultipartFile profileImage) {
		String normalizedEmail = this.normalizeEmail(signUpRequest.email());
		this.checkEmailDuplication(normalizedEmail);
		// TODO : AWS 배포 후 S3 사용시 ProfileImage 저장 로직 개발
		String password = passwordEncoder.encode(signUpRequest.password());

		Users user = Users.builder().email(normalizedEmail).password(password)
			.profileImage(null).nickname(signUpRequest.nickname()).description(null)
			.level(signUpRequest.level()).temperature(50).build(); // temperature는 기본값

		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			if (isEmailUniqueConstraintViolation(e)) {
				throw new DuplicatedEmailException(HttpStatus.CONFLICT, ErrorCodeConstants.EMAIL_ALREADY_EXISTS, "중복된 이메일이 존재합니다.");
			}
			throw e;
		}
	}

	public SignInResponse signIn(SignInRequest request) {
		String normalizedEmail = this.normalizeEmail(request.email());

		try {
			Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
			);
			UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

			JwtToken jwtToken = jwtTokenProvider.generateToken(principal.id(), principal.email());

			return new SignInResponse(
				jwtToken.accessToken(),
				jwtToken.refreshToken(),
				jwtToken.accessTokenExpiresAt()
			);
		} catch (org.springframework.security.core.AuthenticationException exception) {
			throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.", exception);
		}
	}

	public void checkEmailDuplication(String email) {
		String normalizedEmail = this.normalizeEmail(email);

		if (userRepository.existsByEmail(normalizedEmail))
			throw new DuplicatedEmailException(HttpStatus.CONFLICT, ErrorCodeConstants.EMAIL_ALREADY_EXISTS, "중복된 이메일이 존재합니다.");

	}

	public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
		String token = request.refreshToken();
		if (!jwtTokenProvider.validateRefreshToken(token)) {
			throw new InvalidTokenException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
		}

		Long userId = jwtTokenProvider.getUserId(token);
		String email = jwtTokenProvider.getEmail(token);

		Users user = userRepository.findById(userId)
			.orElseThrow(() -> new InvalidTokenException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.UNEXISTED_USER, "존재하지 않는 유저의 리프레시 토큰입니다."));

		if (user.getDeletedAt() != null) {
			throw new InvalidTokenException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.UNEXISTED_USER, "존재하지 않는 유저의 리프레시 토큰입니다.");
		}

		if (!jwtTokenProvider.isRefreshTokenMatched(email, token)) {
			throw new InvalidTokenException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
		}

		String accessToken = jwtTokenProvider.generateAccessToken(userId, user.getEmail());
		return new RefreshTokenResponse(accessToken, jwtTokenProvider.getExpiration(accessToken));
	}

	public GetProfileResponse getMyProfile(Users user) {
		return GetProfileResponse.builder()
			.email(user.getEmail())
			.nickname(user.getNickname())
			.temperature(user.getTemperature())
			.level(user.getLevel())
			.description(user.getDescription())
			.profileImage(user.getProfileImage())
			.build();
	}

	@Transactional
	public void editMyProfile(Users loginUser, MultipartFile profileImage, EditProfileRequest request) {
		Users user = userRepository.findByIdAndDeletedAtIsNull(loginUser.getId()).orElseThrow(
			() -> new UserNotFoundException(
				HttpStatus.UNAUTHORIZED,
				ErrorCodeConstants.UNEXISTED_USER,
				"존재하지 않은 유저입니다."
			));
		user.editDescription(request.description());
		user.editLevel(request.level());
		user.editNickname(request.nickname());
		// TODO : AWS 배포 후 S3 사용시 ProfileImage 수정하는 부분 추가
	}

	@Transactional
	public void editPassword(Users loginUser, EditPasswordRequest request) {
		Users user = userRepository.findByIdAndDeletedAtIsNull(loginUser.getId()).orElseThrow(
			() -> new UserNotFoundException(
				HttpStatus.UNAUTHORIZED,
				ErrorCodeConstants.UNEXISTED_USER,
				"존재하지 않은 유저입니다."
			));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword()))
			throw new InvalidPasswordException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.UNMATCHED_PASSWORD, "현재 비밀번호와 동일하지 않습니다.");

		String newPassword = passwordEncoder.encode(request.afterPassword());
		user.editPassword(newPassword);
		jwtTokenProvider.deleteRefreshToken(user.getEmail());
	}

	@Transactional
	public void deleteUser(Users loginUser, DeleteUserRequest request) {
		Users user = userRepository.findByIdAndDeletedAtIsNull(loginUser.getId()).orElseThrow(
			() -> new UserNotFoundException(
				HttpStatus.UNAUTHORIZED,
				ErrorCodeConstants.UNEXISTED_USER,
				"존재하지 않은 유저입니다."
			));
		if (!passwordEncoder.matches(request.password(), user.getPassword()))
			throw new InvalidPasswordException(HttpStatus.UNAUTHORIZED, ErrorCodeConstants.UNMATCHED_PASSWORD, "현재 비밀번호와 동일하지 않습니다.");

		Instant deletedAt = Instant.now();
		String email = user.getEmail();
		String deletedEmail = createDeletedEmail(user.getId(), email, deletedAt);

		user.softDelete(deletedAt, deletedEmail);
		jwtTokenProvider.deleteRefreshToken(email);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;

		while (cause != null) {
			String message = cause.getMessage();
			if (message != null && message.contains("uq_users_email")) {
				return true;
			}
			cause = cause.getCause();
		}

		return false;
	}

	private String createDeletedEmail(Long userId, String email, Instant deletedAt) {
		return "deleted__" + userId + "__" + deletedAt.toEpochMilli() + "__" + hashEmail(email);
	}

	private String hashEmail(String email) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
		}
	}
}
