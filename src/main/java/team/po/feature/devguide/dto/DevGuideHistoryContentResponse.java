package team.po.feature.devguide.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGenerationType;

public record DevGuideHistoryContentResponse(
	Long devGuideId,
	int versionNo,
	DevGuideGenerationType generationType,
	boolean confirmed,
	LocalDateTime createdAt,
	@JsonUnwrapped DevGuideContent content
) {
	public static DevGuideHistoryContentResponse from(DevGuide devGuide) {
		return new DevGuideHistoryContentResponse(
				devGuide.getId(),
				devGuide.getVersionNo(),
				devGuide.getGenerationType(),
				devGuide.isConfirmed(),
				devGuide.getCreatedAt(),
				devGuide.toContent()
			);
	}
}
