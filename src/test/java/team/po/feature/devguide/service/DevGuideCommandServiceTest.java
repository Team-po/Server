package team.po.feature.devguide.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class DevGuideCommandServiceTest {

	@Mock
	private DevGuideRepository devGuideRepository;

	@Mock
	private DevGuideGenerationRepository devGuideGenerationRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@InjectMocks
	private DevGuideCommandService devGuideCommandService;

	// ─── startInitialGeneration ───────────────────────────────────────────────

	@Test
	void startInitialGeneration_createsGeneratingStatus_whenNoRecordExists() {
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup()));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.empty());

		boolean result = devGuideCommandService.startInitialGeneration(1L);

		assertThat(result).isTrue();
		verify(devGuideGenerationRepository).save(argThat(g -> g.getStatus() == DevGuideStatus.GENERATING));
	}

	@Test
	void startInitialGeneration_updatesToGenerating_whenStatusIsFailed() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration failedGeneration = DevGuideGeneration.create(projectGroup);
		failedGeneration.fail();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(failedGeneration));

		boolean result = devGuideCommandService.startInitialGeneration(1L);

		assertThat(result).isTrue();
		assertThat(failedGeneration.getStatus()).isEqualTo(DevGuideStatus.GENERATING);
	}

	@Test
	void startInitialGeneration_returnsFalse_whenAlreadyGenerating() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generatingStatus = DevGuideGeneration.create(projectGroup);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generatingStatus));

		boolean result = devGuideCommandService.startInitialGeneration(1L);

		assertThat(result).isFalse();
		verify(devGuideGenerationRepository, never()).save(any());
	}

	// ─── create ──────────────────────────────────────────────────────────────

	@Test
	void create_throwsAlreadyExists_whenDevGuideExists() {
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup()));
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(true);

		assertThatThrownBy(() -> devGuideCommandService.create(1L, devGuideContent()))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_ALREADY_EXISTS.getCode());

		verify(devGuideRepository, never()).save(any());
	}

	@Test
	void create_savesGuideAndSetsCompleted() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(false);
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		devGuideCommandService.create(1L, devGuideContent());

		verify(devGuideRepository).save(argThat(g ->
			g.getVersionNo() == 1
				&& g.getGenerationType() == DevGuideGenerationType.INITIAL
				&& g.isConfirmed()
		));
		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.COMPLETED);
	}

	// ─── startRegeneration ───────────────────────────────────────────────────

	@Test
	void startRegeneration_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideCommandService.startRegeneration(1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());
	}

	@Test
	void startRegeneration_throwsGenerating_whenAlreadyGenerating() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		assertThatThrownBy(() -> devGuideCommandService.startRegeneration(1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_GENERATING.getCode());
	}

	@Test
	void startRegeneration_returnsInitial_whenNoGenerationRecordAndNoGuideExists() {
		ProjectGroup projectGroup = projectGroup();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.empty());
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(false);
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(false);

		DevGuideGenerationType result = devGuideCommandService.startRegeneration(1L);

		assertThat(result).isEqualTo(DevGuideGenerationType.INITIAL);
		verify(devGuideGenerationRepository).save(argThat(g -> g.getStatus() == DevGuideStatus.GENERATING));
	}

	@Test
	void startRegeneration_returnsManual_whenStatusIsFailedAndConfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.fail();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(true);
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(0);

		DevGuideGenerationType result = devGuideCommandService.startRegeneration(1L);

		assertThat(result).isEqualTo(DevGuideGenerationType.MANUAL);
		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.GENERATING);
	}

	@Test
	void startRegeneration_throwsNotFound_whenOnlyUnconfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(false);
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(true);

		assertThatThrownBy(() -> devGuideCommandService.startRegeneration(1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_NOT_FOUND.getCode());

		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.COMPLETED);
		verify(devGuideGenerationRepository, never()).save(any());
	}

	@Test
	void startRegeneration_returnsManual_whenCompletedAndConfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(true);
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(0);

		DevGuideGenerationType result = devGuideCommandService.startRegeneration(1L);

		assertThat(result).isEqualTo(DevGuideGenerationType.MANUAL);
	}

	@Test
	void startRegeneration_throwsLimitExceeded_whenManualCountReachesMax() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(true);
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(3); // default max is 3

		assertThatThrownBy(() -> devGuideCommandService.startRegeneration(1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_REGENERATION_LIMIT_EXCEEDED.getCode());

		verify(devGuideGenerationRepository, never()).save(any());
	}

	@Test
	void startRegeneration_throwsLimitExceeded_whenStatusIsFailedAndConfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.fail();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L)).thenReturn(true);
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(3); // default max is 3

		assertThatThrownBy(() -> devGuideCommandService.startRegeneration(1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_REGENERATION_LIMIT_EXCEEDED.getCode());

		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.FAILED);
		verify(devGuideGenerationRepository, never()).save(any());
	}

	// ─── completeRegeneration ────────────────────────────────────────────────

	@Test
	void completeRegeneration_unconfirmsOldAndSavesNewVersionAsConfirmed() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		DevGuide existingConfirmed = DevGuide.create(projectGroup, devGuideContent(), 1,
			DevGuideGenerationType.INITIAL, true);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L))
			.thenReturn(Optional.of(existingConfirmed));
		when(devGuideRepository.findMaxVersionNoByProjectGroupId(1L)).thenReturn(1);
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(1);

		int remaining = devGuideCommandService.completeRegeneration(1L, content, DevGuideGenerationType.MANUAL);

		assertThat(existingConfirmed.isConfirmed()).isFalse();
		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.COMPLETED);
		assertThat(remaining).isEqualTo(2); // max(3) - manual(1) = 2
		verify(devGuideRepository).save(argThat(g ->
			g.getVersionNo() == 2
				&& g.getGenerationType() == DevGuideGenerationType.MANUAL
				&& g.isConfirmed()
		));
	}

	// ─── confirm ─────────────────────────────────────────────────────────────

	@Test
	void confirm_unconfirmsExistingGuideAndConfirmsTarget() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();
		DevGuide existingConfirmed = DevGuide.create(projectGroup, devGuideContent(), 1,
			DevGuideGenerationType.INITIAL, true);
		DevGuide target = DevGuide.create(projectGroup, devGuideContent(), 2,
			DevGuideGenerationType.MANUAL, false);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.findByIdAndProjectGroup_IdAndDeletedAtIsNull(2L, 1L))
			.thenReturn(Optional.of(target));
		when(devGuideRepository.findAllByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(1L))
			.thenReturn(List.of(existingConfirmed));

		devGuideCommandService.confirm(1L, 2L);

		assertThat(existingConfirmed.isConfirmed()).isFalse();
		assertThat(target.isConfirmed()).isTrue();
	}

	@Test
	void confirm_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideCommandService.confirm(1L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());

		verify(devGuideRepository, never()).findByIdAndProjectGroup_IdAndDeletedAtIsNull(any(), any());
	}

	@Test
	void confirm_throwsWriteNotAllowed_whenProjectGroupIsFinished() {
		ProjectGroup projectGroup = projectGroup();
		projectGroup.finish();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));

		assertThatThrownBy(() -> devGuideCommandService.confirm(1L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_WRITE_NOT_ALLOWED.getCode());

		verify(devGuideRepository, never()).findByIdAndProjectGroup_IdAndDeletedAtIsNull(any(), any());
	}

	@Test
	void confirm_throwsGenerating_whenDevGuideGenerationIsInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		assertThatThrownBy(() -> devGuideCommandService.confirm(1L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_GENERATING.getCode());

		verify(devGuideRepository, never()).findByIdAndProjectGroup_IdAndDeletedAtIsNull(any(), any());
	}

	@Test
	void confirm_throwsNotFound_whenTargetDevGuideDoesNotExistInProjectGroup() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.findByIdAndProjectGroup_IdAndDeletedAtIsNull(2L, 1L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideCommandService.confirm(1L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_NOT_FOUND.getCode());

		verify(devGuideRepository, never()).findAllByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(any());
	}

	// ─── failGeneration ──────────────────────────────────────────────────────

	@Test
	void failGeneration_setsStatusToFailed_whenGenerating() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		devGuideCommandService.failGeneration(1L);

		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.FAILED);
	}

	@Test
	void failGeneration_doesNotOverwriteCompleted() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		devGuideCommandService.failGeneration(1L);

		assertThat(generation.getStatus()).isEqualTo(DevGuideStatus.COMPLETED);
	}

	// ─── recoverStaleGenerationsOnStartup ────────────────────────────────────

	@Test
	void recoverStaleGenerationsOnStartup_failsAllGeneratingStates() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration gen1 = DevGuideGeneration.create(projectGroup);
		DevGuideGeneration gen2 = DevGuideGeneration.create(projectGroup);

		when(devGuideGenerationRepository.findAllByStatusAndUpdatedAtBefore(
			eq(DevGuideStatus.GENERATING), any(LocalDateTime.class)))
			.thenReturn(List.of(gen1, gen2));

		devGuideCommandService.recoverStaleGenerationsOnStartup();

		assertThat(gen1.getStatus()).isEqualTo(DevGuideStatus.FAILED);
		assertThat(gen2.getStatus()).isEqualTo(DevGuideStatus.FAILED);
	}

	// ─── fixtures ────────────────────────────────────────────────────────────

	private ProjectGroup projectGroup() {
		return ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
	}

	private DevGuideContent devGuideContent() {
		return new DevGuideContent(
			"프로젝트 개요입니다.",
			List.of(
				new DevGuideContent.TechStackItem("Backend", "Spring Boot", "학습 자료가 많습니다."),
				new DevGuideContent.TechStackItem("Frontend", "React", "컴포넌트 기반에 적합합니다."),
				new DevGuideContent.TechStackItem("Database", "MySQL", "관계형 데이터에 적합합니다."),
				new DevGuideContent.TechStackItem("Infra", "Docker", "환경 통일에 적합합니다."),
				new DevGuideContent.TechStackItem("CI/CD", "GitHub Actions", "자동화에 적합합니다.")
			),
			List.of(
				new DevGuideContent.MvpPriority(1, "로그인", "가장 먼저 필요합니다.", List.of("회원가입", "로그인", "토큰 발급")),
				new DevGuideContent.MvpPriority(2, "팀 생성", "핵심 흐름입니다.", List.of("팀 생성", "멤버 추가", "권한 설정")),
				new DevGuideContent.MvpPriority(3, "가이드 조회", "팀 생성 이후 필요합니다.",
					List.of("가이드 생성", "가이드 저장", "가이드 조회"))
			),
			List.of(
				new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "사용자 경험을 고려합니다."),
				new DevGuideContent.DecisionPoint("팀 가입 방식", List.of("자동", "승인"), "팀 관리 정책에 영향을 줍니다."),
				new DevGuideContent.DecisionPoint("재생성 정책", List.of("제한 없음", "횟수 제한"), "API 비용에 영향을 줍니다.")
			),
			List.of(
				milestone(1), milestone(2), milestone(3), milestone(4),
				milestone(5), milestone(6), milestone(7), milestone(8),
				milestone(9), milestone(10), milestone(11), milestone(12)
			)
		);
	}

	private DevGuideContent.Milestone milestone(int week) {
		return new DevGuideContent.Milestone(week, week + "주차 목표",
			new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업"));
	}
}
