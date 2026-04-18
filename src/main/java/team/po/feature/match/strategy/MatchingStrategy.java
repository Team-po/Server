package team.po.feature.match.strategy;

import java.util.Optional;

public interface MatchingStrategy {
	Optional<MatchingResult> findCandidates(MatchingContext context);
}
