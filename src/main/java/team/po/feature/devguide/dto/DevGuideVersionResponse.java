package team.po.feature.devguide.dto;

import java.time.LocalDateTime;

import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGenerationType;

public record DevGuideVersionResponse(
	Long devGuideId,
	int versionNo,
	DevGuideGenerationType generationType,
	boolean confirmed,
	LocalDateTime createdAt
) {
	public static DevGuideVersionResponse from(DevGuide devGuide) {
		return new DevGuideVersionResponse(
			devGuide.getId(),
			devGuide.getVersionNo(),
			devGuide.getGenerationType(),
			devGuide.isConfirmed(),
			devGuide.getCreatedAt()
		);
	}
}
