package team.po.feature.match.strategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import team.po.feature.match.domain.ProjectRequest;

@Component
public class DefaultMatchingScorer implements MatchingScorer {
	/**
	 * 가중합 점수 산식
	 * - temperature * 0.4        : 활동 신뢰도 반영
	 * - 대기시간 (최대 40점)		  : 오래 기다린 유저 우선
	 * - 레벨 근접도 * 2           : 팀장과 비슷한 레벨 선호
	 */
	@Override
	public double calculateScore(int hostLevel, ProjectRequest candidate) {
		// 1. 온도 점수 (0-100 * 0.5)
		double temperatureScore = candidate.getUser().getTemperature() * 0.5;

		// 2. 대기 시간 점수: 최대 48시간 기준 (최대 40점)
		long waitingMinutes = ChronoUnit.MINUTES.between(candidate.getCreatedAt(), Instant.now());
		double waitingScore = (Math.min(waitingMinutes, MatchConstants.SCORE_WAITING_LIMIT_MIN)
			/ (double)MatchConstants.SCORE_WAITING_LIMIT_MIN) * 40.0;

		// 3. 레벨 근접도 점수
		double levelScore = (10 - Math.abs(hostLevel - candidate.getUser().getLevel())) * 2.0;

		return temperatureScore + waitingScore + levelScore;
	}
}
