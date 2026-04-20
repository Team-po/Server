package team.po.feature.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import team.po.feature.user.domain.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
	public Boolean existsByEmail(String email);

	public Optional<Users> findByEmail(String email);

	public Optional<Users> findById(Long id);

	public Optional<Users> findByIdAndDeletedAtIsNull(Long id);

	public Optional<Users> findByEmailAndDeletedAtIsNull(String email);

	public List<Users> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Users u where u.id in :ids and u.deletedAt is null order by u.id")
	List<Users> findAllByIdInAndDeletedAtIsNullForUpdate(@Param("ids") List<Long> ids);
}
