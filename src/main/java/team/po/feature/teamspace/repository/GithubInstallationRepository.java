package team.po.feature.teamspace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.GithubInstallation;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, Long> {
	Optional<GithubInstallation> findByInstallationIdAndDeletedAtIsNull(Long installationId);
}
