package team.po.feature.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.user.domain.GithubAccount;

public interface GithubAccountRepository extends JpaRepository<GithubAccount, Long> {
	Optional<GithubAccount> findByGithubUserIdAndDeletedAtIsNull(Long githubUserId);

	Optional<GithubAccount> findByUserIdAndDeletedAtIsNull(Long userId);
}
