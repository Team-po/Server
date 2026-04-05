package team.po.feature.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.user.domain.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
	public Boolean existsByEmail(String email);

	public Optional<Users> findByEmail(String email);

	public Optional<Users> findById(Long id);

	public Optional<Users> findByIdAndDeletedAtIsNull(Long id);

	public Optional<Users> findByEmailAndDeletedAtIsNull(String email);
}
