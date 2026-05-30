package team.po.feature.match.dto;

import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;

public record ProjectRequestStatusResponse(
	Status status,
	Role role
) {
}
