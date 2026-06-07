package team.po.feature.devguide.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGeneration;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.domain.DevGuideStatus;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.repository.DevGuideGenerationRepository;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class DevGuideCommandService {
	private final DevGuideRepository devGuideRepository;
	private final DevGuideGenerationRepository devGuideGenerationRepository;
	private final ProjectGroupRepository projectGroupRepository;

	/**
	 * 이벤트 트리거 시 호출. 생성 상태를 GENERATING으로 초기화한다.
	 * 이미 GENERATING 중이면 중복 실행을 방지하고 false를 반환한다.
	 */
	@Transactional
	public boolean startInitialGeneration(Long projectGroupId) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		Optional<DevGuideGeneration> existing = devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId);

		if (existing.isPresent()) {
			if (existing.get().isGenerating()) {
				return false;
			}
			existing.get().startGenerating();
			return true;
		}

		devGuideGenerationRepository.save(DevGuideGeneration.create(projectGroup));
		return true;
	}

	@Transactional
	public void create(Long projectGroupId, DevGuideContent content) {
		// 최초 생성 시 이미 가이드라인이 존재하는지 확인
		if (devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_ALREADY_EXISTS);
		}
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		// 최초 생성 성공 시 현재 기준 가이드라인으로 자동 확정한다.
		devGuideRepository.save(
			DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true)
		);

		devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId)
			.ifPresent(DevGuideGeneration::complete);
	}

	// 재생성 시 Gemini 호출 전에 lock 획득 후 generation 상태를 GENERATING으로 전환한다.
	@Transactional
	public DevGuideGenerationType startRegeneration(Long projectGroupId) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_WRITE_NOT_ALLOWED);
		}

		Optional<DevGuideGeneration> existing = devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId);

		if (existing.isPresent() && existing.get().isGenerating()) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING);
		}

		DevGuideGeneration generation = existing.orElseGet(() -> DevGuideGeneration.create(projectGroup));

		int manualCount = devGuideRepository.countByProjectGroup_IdAndGenerationType(
			projectGroupId, DevGuideGenerationType.MANUAL);
		if (manualCount >= generation.getMaxRegenerationCount()) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_REGENERATION_LIMIT_EXCEEDED);
		}

		boolean hasConfirmed = devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId);
		DevGuideGenerationType generationType;

		// FAILED 또는 isConfirmed 가이드라인이 없으면 RECOVERY, 성공 횟수는 completeRegeneration에서 MANUAL만 차감
		if (generation.getStatus() == DevGuideStatus.FAILED || !hasConfirmed) {
			generationType = DevGuideGenerationType.RECOVERY;
		} else { // COMPLETED 상태이면서 isConfirmed 가이드라인이 있으면 MANUAL, 재생성 횟수 차감
			generationType = DevGuideGenerationType.MANUAL;
		}

		generation.startGenerating();
		devGuideGenerationRepository.save(generation);
		// 생성 타입 리턴
		return generationType;
	}

	@Transactional
	public int completeRegeneration(Long projectGroupId, DevGuideContent content,
		DevGuideGenerationType generationType) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)
			.ifPresent(DevGuide::unconfirm);

		int nextVersionNo = devGuideRepository.findMaxVersionNoByProjectGroupId(projectGroupId) + 1;
		// 재생성 성공 시 새 버전을 현재 기준 가이드라인으로 자동 확정한다.
		devGuideRepository.save(DevGuide.create(projectGroup, content, nextVersionNo, generationType, true));

		DevGuideGeneration generation = devGuideGenerationRepository
			.findByProjectGroup_Id(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));
		generation.complete();

		int manualCount = devGuideRepository.countByProjectGroup_IdAndGenerationType(
			projectGroupId, DevGuideGenerationType.MANUAL);
		return generation.getMaxRegenerationCount() - manualCount;
	}

	@Transactional
	public void confirm(Long projectGroupId, Long devGuideId) {
		// 프로젝트 그룹 조회
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		// 종료된 프로젝트에서는 가이드라인 확정 불가
		if (projectGroup.getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_WRITE_NOT_ALLOWED);
		}

		// 생성 중인 가이드라인이 존재하면 확정 불가
		devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId)
			.filter(DevGuideGeneration::isGenerating)
			.ifPresent(generation -> {
				throw new ApplicationException(ErrorCode.DEV_GUIDE_GENERATING);
			});

		// 확정할 가이드라인 조회
		DevGuide target = devGuideRepository.findByIdAndProjectGroup_IdAndDeletedAtIsNull(devGuideId, projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));

		// 이미 확정된 가이드라인이 있다면 해제하고 target 가이드라인 확정
		devGuideRepository.findAllByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(projectGroupId)
			.forEach(DevGuide::unconfirm);
		target.confirm();
	}

	/**
	 * 서버 기동 시 일정 시간(STALE_THRESHOLD_MINUTES) 이상 갱신되지 않은 GENERATING 레코드를 FAILED로 전환한다.
	 * 블루/그린 배포 시 신규 인스턴스가 기동되더라도, 기존 인스턴스가 처리 중인 최근 작업은 건드리지 않는다.
	 */
	private static final int STALE_THRESHOLD_MINUTES = 10;

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void recoverStaleGenerationsOnStartup() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
		devGuideGenerationRepository.findAllByStatusAndUpdatedAtBefore(DevGuideStatus.GENERATING, threshold)
			.forEach(DevGuideGeneration::fail);
	}

	/**
	 * 생성/재생성 실패 시 호출. 상태를 FAILED로 전환한다.
	 */
	@Transactional
	public void failGeneration(Long projectGroupId) {
		// GENERATING 상태인 경우에만 FAILED로 전환 (COMPLETED 상태를 덮어쓰지 않도록)
		devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId)
			.filter(DevGuideGeneration::isGenerating)
			.ifPresent(DevGuideGeneration::fail);
	}
}
