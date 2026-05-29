package team.po.feature.teamspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.GithubPullRequestContribution;

public interface GithubPullRequestContributionRepository extends JpaRepository<GithubPullRequestContribution, Long> {
}
