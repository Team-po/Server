package team.po.feature.devguide.dto;

import java.util.List;

public record DevGuideVersionListResponse(
	List<DevGuideVersionResponse> versions
) {
}
