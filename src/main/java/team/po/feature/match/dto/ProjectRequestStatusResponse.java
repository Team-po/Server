package team.po.feature.match.dto;

import team.po.feature.match.enums.Status;

public record ProjectRequestStatusResponse(
	Status status,
	Long matchId
) {
}
