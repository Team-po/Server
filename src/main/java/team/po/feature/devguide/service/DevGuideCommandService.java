package team.po.feature.devguide.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import java.util.Optional;

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
	public DevGuideGenerationType regenerate(Long projectGroupId, DevGuideContent content) {
		// 동일 프로젝트 그룹 내 재생성 동시 요청 serialize (lock 획득)
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		// lock 획득 후 상태 확인 → 타입 자동 결정
		// confirmed 가이드 있음 → MANUAL (횟수 차감), 없음 → RECOVERY (횟수 미차감)
		Optional<DevGuide> currentConfirmed =
			devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId);

		DevGuideGenerationType generationType;
		if (currentConfirmed.isPresent()) {
			// 횟수 제한 체크 자리 (다음 커밋에서 구현)
			// int regenerationCount = devGuideRepository.countByProjectGroup_Id(projectGroupId) - 1;
			currentConfirmed.get().unconfirm();
			generationType = DevGuideGenerationType.MANUAL;
		} else {
			generationType = DevGuideGenerationType.RECOVERY;
		}

		int nextVersionNo = devGuideRepository.findMaxVersionNoByProjectGroupId(projectGroupId) + 1;

		devGuideRepository.save(
			DevGuide.create(projectGroup, content, nextVersionNo, generationType, true)
		);

		return generationType;
	}

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
