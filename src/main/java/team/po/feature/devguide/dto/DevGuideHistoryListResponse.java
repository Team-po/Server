package team.po.feature.devguide.dto;

import java.util.List;

public record DevGuideHistoryListResponse(
	List<DevGuideHistoryResponse> histories
) {
}
