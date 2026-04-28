package team.po.feature.match.strategy;

import team.po.feature.match.domain.ProjectRequest;

public interface MatchingScorer {
	// host와 candidate 사이 점수 계산
	double calculateScore(int hostLevel, ProjectRequest candidate);
}
