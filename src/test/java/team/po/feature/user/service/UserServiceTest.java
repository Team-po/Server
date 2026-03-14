package team.po.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import team.po.feature.user.domain.Users;
import team.po.feature.user.dto.SignUpRequest;
import team.po.feature.user.exception.DuplicatedEmailException;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

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
		assertThat(savedUser.getDescription()).isEqualTo("");
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
}
