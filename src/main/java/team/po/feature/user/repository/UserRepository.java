package team.po.feature.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.user.domain.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
}
