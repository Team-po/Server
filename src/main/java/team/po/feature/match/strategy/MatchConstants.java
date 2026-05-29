package team.po.feature.match.strategy;

import java.util.Map;

import team.po.feature.match.enums.Role;

public class MatchConstants {
	public static final int TEAM_SIZE = 4;
	// 팀 구성: BACKEND 2, FRONTEND 1, DESIGN 1 (팀장 포함)
	// 팀장 포지션에 따라 나머지 3자리 조합 결정
	public static final Map<Role, Map<Role, Integer>> REQUIRED_NON_HOST = Map.of(
		Role.BACKEND, Map.of(Role.BACKEND, 1, Role.FRONTEND, 1, Role.DESIGN, 1),
		Role.FRONTEND, Map.of(Role.BACKEND, 2, Role.DESIGN, 1),
		Role.DESIGN, Map.of(Role.BACKEND, 2, Role.FRONTEND, 1)
	);

	// 멤버 간 레벨 차이를 2로 제한
	public static final int LEVEL_RANGE = 2;

	// 대기 시간 점수 상한선(대기 최대 48시간)
	public static final int SCORE_WAITING_LIMIT_MIN = 2880;
}
