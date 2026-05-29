package team.po.feature.devguide.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class DevGuideCommandService {
	private final DevGuideRepository devGuideRepository;
	private final ProjectGroupRepository projectGroupRepository;

	@Transactional
	public void create(Long projectGroupId, DevGuideContent content) {
		if (devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_ALREADY_EXISTS);
		}
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		// Gemini 응답 DB 저장
		devGuideRepository.save(
			// 최초 생성이므로 version=1, type=INITIAL, confirmed=true
			DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true)
		);
	}
}
