package team.po.feature.user.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public void signUp(SignUpRequest signUpRequest, MultipartFile profileImage) {
		String normalizedEmail = normalizeEmail(signUpRequest.email());

		if (userRepository.existsByEmail(normalizedEmail))
			throw new DuplicatedEmailException(HttpStatus.CONFLICT, ErrorCodeConstants.EMAIL_ALREADY_EXISTS, "중복된 이메일이 존재합니다.");

		// TODO : AWS 배포 후 S3 사용시 ProfileImage 저장 로직 개발
		String password = passwordEncoder.encode(signUpRequest.password());
		Users user = Users.builder().email(normalizedEmail).password(password)
			.profileImage(null).nickname(signUpRequest.nickname()).description(null).level(3).temperature(50).build(); // level과 temperature은 기본값

		try {
			userRepository.save(user);
		} catch (DataIntegrityViolationException e) {
			if (isEmailUniqueConstraintViolation(e)) {
				throw new DuplicatedEmailException(HttpStatus.CONFLICT, ErrorCodeConstants.EMAIL_ALREADY_EXISTS, "중복된 이메일이 존재합니다.");
			}
			throw e;
		}
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

}
