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
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
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

	/**
	 * 초기 가이드라인 생성 완료 시 호출. DevGuide 저장 후 상태를 COMPLETED로 전환한다.
	 */
	@Transactional
	public void create(Long projectGroupId, DevGuideContent content) {
		if (devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)) {
			throw new ApplicationException(ErrorCode.DEV_GUIDE_ALREADY_EXISTS);
		}
		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		devGuideRepository.save(
			DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true)
		);

		devGuideGenerationRepository.findByProjectGroup_Id(projectGroupId)
			.ifPresent(DevGuideGeneration::complete);
	}

	/**
	 * 재생성 요청 시 Gemini 호출 전에 호출. lock 획득 후 조건을 검증하고 상태를 GENERATING으로 전환한다.
	 *
	 * 타입 결정 기준:
	 *   - FAILED 상태 또는 confirmed 가이드 없음 → RECOVERY (횟수 미차감, 실패 재시도)
	 *   - COMPLETED 이고 confirmed 가이드 있음    → MANUAL   (횟수 차감)
	 *
	 * @return 결정된 생성 타입 (Gemini 호출 후 completeRegeneration에 전달)
	 */
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

		boolean hasConfirmed = devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(projectGroupId);
		DevGuideGenerationType generationType;

		if (generation.getStatus() == DevGuideStatus.FAILED || !hasConfirmed) {
			generationType = DevGuideGenerationType.RECOVERY;
		} else {
			int manualCount = devGuideRepository.countByProjectGroup_IdAndGenerationType(
				projectGroupId, DevGuideGenerationType.MANUAL);
			if (manualCount >= generation.getMaxRegenerationCount()) {
				throw new ApplicationException(ErrorCode.DEV_GUIDE_REGENERATION_LIMIT_EXCEEDED);
			}
			generationType = DevGuideGenerationType.MANUAL;
		}

		generation.startGenerating();
		devGuideGenerationRepository.save(generation);
		return generationType;
	}

	/**
	 * 재생성 Gemini 호출 성공 후 호출. 기존 confirmed 가이드를 해제하고 새 버전을 저장한 뒤 COMPLETED로 전환한다.
	 *
	 * @return 남은 재생성 횟수 (MANUAL 횟수 기준)
	 */
	@Transactional
	public int completeRegeneration(Long projectGroupId, DevGuideContent content,
		DevGuideGenerationType generationType) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(projectGroupId)
			.ifPresent(DevGuide::unconfirm);

		int nextVersionNo = devGuideRepository.findMaxVersionNoByProjectGroupId(projectGroupId) + 1;
		devGuideRepository.save(DevGuide.create(projectGroup, content, nextVersionNo, generationType, true));

		DevGuideGeneration generation = devGuideGenerationRepository
			.findByProjectGroup_Id(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));
		generation.complete();

		int manualCount = devGuideRepository.countByProjectGroup_IdAndGenerationType(
			projectGroupId, DevGuideGenerationType.MANUAL);
		return generation.getMaxRegenerationCount() - manualCount;
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