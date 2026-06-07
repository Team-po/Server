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
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.client.GeminiClient;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGeneration;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.domain.DevGuideStatus;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.dto.DevGuideQueryResponse;
import team.po.feature.devguide.dto.DevGuideRegenerateResponse;
import team.po.feature.devguide.dto.DevGuideVersionListResponse;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.repository.DevGuideGenerationRepository;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@ExtendWith(MockitoExtension.class)
class DevGuideServiceTest {

	@Mock
	private DevGuideRepository devGuideRepository;

	@Mock
	private DevGuideGenerationRepository devGuideGenerationRepository;

	@Mock
	private GeminiClient geminiClient;

	@Mock
	private DevGuidePromptBuilder promptBuilder;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@Mock
	private DevGuideCommandService devGuideCommandService;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@InjectMocks
	private DevGuideService devGuideService;

	// ─── generate ────────────────────────────────────────────────────────────

	@Test
	void generate_skips_whenDevGuideAlreadyExists() {
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(true);

		devGuideService.generate(1L);

		verify(devGuideCommandService, never()).startInitialGeneration(any());
		verify(geminiClient, never()).generateDevGuide(any(), any());
	}

	@Test
	void generate_skips_whenAlreadyGenerating() {
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(false);
		when(devGuideCommandService.startInitialGeneration(1L)).thenReturn(false);

		devGuideService.generate(1L);

		verify(geminiClient, never()).generateDevGuide(any(), any());
	}

	@Test
	void generate_callsGeminiAndCreate_whenStartSucceeds() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(false);
		when(devGuideCommandService.startInitialGeneration(1L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup));
		when(promptBuilder.build("주제 A", "설명", "MVP")).thenReturn("prompt");
		when(geminiClient.generateDevGuide(eq("prompt"), any())).thenReturn(content);

		devGuideService.generate(1L);

		verify(devGuideCommandService).create(1L, content);
		verify(devGuideCommandService, never()).failGeneration(any());
	}

	@Test
	void generate_callsFailGeneration_andRethrows_whenGeminiFails() {
		when(devGuideRepository.existsByProjectGroup_IdAndDeletedAtIsNull(1L)).thenReturn(false);
		when(devGuideCommandService.startInitialGeneration(1L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup()));
		when(promptBuilder.build(any(), any(), any())).thenReturn("prompt");
		when(geminiClient.generateDevGuide(any(), any())).thenThrow(new RuntimeException("Gemini error"));

		assertThatThrownBy(() -> devGuideService.generate(1L))
			.isInstanceOf(RuntimeException.class);

		verify(devGuideCommandService).failGeneration(1L);
	}

	// ─── regenerate ──────────────────────────────────────────────────────────

	@Test
	void regenerate_throwsAccessDenied_whenUserIsNotMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(false);

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L, null))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode());

		verify(devGuideCommandService, never()).startRegeneration(any());
	}

	@Test
	void regenerate_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L, null))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());

		verify(devGuideCommandService, never()).startRegeneration(any());
	}

	@Test
	void regenerate_callsFailGeneration_andRethrows_whenGeminiFails() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup()));
		when(devGuideCommandService.startRegeneration(1L)).thenReturn(DevGuideGenerationType.MANUAL);
		when(promptBuilder.build(any(), any(), any())).thenReturn("prompt");
		when(geminiClient.generateDevGuide(any(), any())).thenThrow(new RuntimeException("Gemini error"));

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L, null))
			.isInstanceOf(RuntimeException.class);

		verify(devGuideCommandService).failGeneration(1L);
	}

	@Test
	void regenerate_returnsResponse_withManualType() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideCommandService.startRegeneration(1L)).thenReturn(DevGuideGenerationType.MANUAL);
		when(promptBuilder.build(eq("주제 A"), eq("설명"), eq("MVP"), any())).thenReturn("prompt");
		when(geminiClient.generateDevGuide(eq("prompt"), any())).thenReturn(content);
		when(devGuideCommandService.completeRegeneration(1L, content, DevGuideGenerationType.MANUAL)).thenReturn(2);

		DevGuideRegenerateResponse result = devGuideService.regenerate(1L, 10L, null);

		assertThat(result.generationType()).isEqualTo(DevGuideGenerationType.MANUAL);
		assertThat(result.remainingRegenerationCount()).isEqualTo(2);
		assertThat(result.content()).isEqualTo(content);
		verify(devGuideCommandService, never()).failGeneration(any());
	}

	@Test
	void regenerate_returnsResponse_withRecoveryType() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideCommandService.startRegeneration(1L)).thenReturn(DevGuideGenerationType.RECOVERY);
		when(promptBuilder.build(eq("주제 A"), eq("설명"), eq("MVP"), any())).thenReturn("prompt");
		when(geminiClient.generateDevGuide(eq("prompt"), any())).thenReturn(content);
		when(devGuideCommandService.completeRegeneration(1L, content, DevGuideGenerationType.RECOVERY)).thenReturn(3);

		DevGuideRegenerateResponse result = devGuideService.regenerate(1L, 10L, null);

		assertThat(result.generationType()).isEqualTo(DevGuideGenerationType.RECOVERY);
		assertThat(result.remainingRegenerationCount()).isEqualTo(3);
	}

	// ─── getDevGuide ─────────────────────────────────────────────────────────

	@Test
	void getDevGuide_returnsContentAndRemainingCount_whenCompletedAndConfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();
		DevGuide devGuide = DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true);
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.of(devGuide));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(1);

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.getGenerationStatus()).isEqualTo(DevGuideStatus.COMPLETED);
		assertThat(result.getContent()).isNotNull();
		assertThat(result.getRemainingRegenerationCount()).isEqualTo(2); // max(3) - manual(1)
	}

	@Test
	void getDevGuide_returnsContentWithGeneratingStatus_whenRegenerationInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();
		DevGuide devGuide = DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true);
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		// status stays GENERATING (startRegeneration set it)

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.of(devGuide));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(0);

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		// 재생성 진행 중 → 기존 가이드 유지, GENERATING 상태 반환
		assertThat(result.getGenerationStatus()).isEqualTo(DevGuideStatus.GENERATING);
		assertThat(result.getContent()).isNotNull();
	}

	@Test
	void getDevGuide_returnsContentWithFailedStatus_whenRegenerationFailed() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();
		DevGuide devGuide = DevGuide.create(projectGroup, content, 1, DevGuideGenerationType.INITIAL, true);
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();
		generation.startGenerating();
		generation.fail();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.of(devGuide));
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.countByProjectGroup_IdAndGenerationType(1L, DevGuideGenerationType.MANUAL))
			.thenReturn(0);

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		// 재생성 실패 → 기존 가이드 유지, FAILED 상태 반환 (프론트는 재시도 버튼 표시)
		assertThat(result.getGenerationStatus()).isEqualTo(DevGuideStatus.FAILED);
		assertThat(result.getContent()).isNotNull();
	}

	@Test
	void getDevGuide_returnsGeneratingStatus_whenInitialGenerationInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.getGenerationStatus()).isEqualTo(DevGuideStatus.GENERATING);
		assertThat(result.getContent()).isNull();
	}

	@Test
	void getDevGuide_returnsFailedStatus_whenInitialGenerationFailed() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.fail();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.getGenerationStatus()).isEqualTo(DevGuideStatus.FAILED);
		assertThat(result.getContent()).isNull();
	}

	@Test
	void getDevGuide_throwsNotFound_whenNeitherGuideNorGenerationRecordExists() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideService.getDevGuide(1L, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_NOT_FOUND.getCode());
	}

	@Test
	void getDevGuide_throwsAccessDenied_whenUserIsNotMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(false);

		assertThatThrownBy(() -> devGuideService.getDevGuide(1L, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode());

		verify(devGuideRepository, never()).findByProjectGroup_IdAndIsConfirmedTrue(any());
	}

	// ─── getVersions ─────────────────────────────────────────────────────────

	@Test
	void getVersions_returnsVersionList_whenGenerationIsNotInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.complete();
		DevGuide version2 = devGuide(projectGroup, 2L, 2, DevGuideGenerationType.MANUAL, true,
			LocalDateTime.of(2026, 6, 7, 12, 30));
		DevGuide version1 = devGuide(projectGroup, 1L, 1, DevGuideGenerationType.INITIAL, false,
			LocalDateTime.of(2026, 6, 7, 12, 0));

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));
		when(devGuideRepository.findAllByProjectGroup_IdAndDeletedAtIsNullOrderByVersionNoDesc(1L))
			.thenReturn(List.of(version2, version1));

		DevGuideVersionListResponse result = devGuideService.getVersions(1L, 10L);

		assertThat(result.versions()).hasSize(2);
		assertThat(result.versions().get(0).devGuideId()).isEqualTo(2L);
		assertThat(result.versions().get(0).versionNo()).isEqualTo(2);
		assertThat(result.versions().get(0).generationType()).isEqualTo(DevGuideGenerationType.MANUAL);
		assertThat(result.versions().get(0).confirmed()).isTrue();
		assertThat(result.versions().get(1).devGuideId()).isEqualTo(1L);
		assertThat(result.versions().get(1).versionNo()).isEqualTo(1);
		assertThat(result.versions().get(1).confirmed()).isFalse();
	}

	@Test
	void getVersions_returnsEmptyList_whenNoDevGuideExists() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.empty());
		when(devGuideRepository.findAllByProjectGroup_IdAndDeletedAtIsNullOrderByVersionNoDesc(1L))
			.thenReturn(List.of());

		DevGuideVersionListResponse result = devGuideService.getVersions(1L, 10L);

		assertThat(result.versions()).isEmpty();
	}

	@Test
	void getVersions_throwsGenerating_whenDevGuideGenerationIsInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		assertThatThrownBy(() -> devGuideService.getVersions(1L, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_GENERATING.getCode());

		verify(devGuideRepository, never()).findAllByProjectGroup_IdAndDeletedAtIsNullOrderByVersionNoDesc(any());
	}

	@Test
	void getVersions_throwsAccessDenied_whenUserIsNotMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(false);

		assertThatThrownBy(() -> devGuideService.getVersions(1L, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode());

		verify(devGuideGenerationRepository, never()).findByProjectGroup_Id(any());
	}

	// ─── confirm ─────────────────────────────────────────────────────────────

	@Test
	void confirm_delegatesToCommandService_whenUserIsMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);

		devGuideService.confirm(1L, 10L, 2L);

		verify(devGuideCommandService).confirm(1L, 2L);
	}

	@Test
	void confirm_throwsAccessDenied_whenUserIsNotMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(false);

		assertThatThrownBy(() -> devGuideService.confirm(1L, 10L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode());

		verify(devGuideCommandService, never()).confirm(any(), any());
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

	private DevGuide devGuide(ProjectGroup projectGroup, Long id, int versionNo,
		DevGuideGenerationType generationType, boolean confirmed, LocalDateTime createdAt) {
		DevGuide devGuide = DevGuide.create(projectGroup, devGuideContent(), versionNo, generationType, confirmed);
		ReflectionTestUtils.setField(devGuide, "id", id);
		ReflectionTestUtils.setField(devGuide, "createdAt", createdAt);
		return devGuide;
	}

	private DevGuideContent devGuideContent() {
		return new DevGuideContent(
			"프로젝트 개요입니다.",
			List.of(
				new DevGuideContent.TechStackItem("Backend", "Spring Boot", "학습 자료가 많고 구현하기 적합합니다."),
				new DevGuideContent.TechStackItem("Frontend", "React", "컴포넌트 기반 개발에 적합합니다."),
				new DevGuideContent.TechStackItem("Database", "MySQL", "관계형 데이터 저장에 적합합니다."),
				new DevGuideContent.TechStackItem("Infra", "Docker", "개발 환경 통일에 적합합니다."),
				new DevGuideContent.TechStackItem("CI/CD", "GitHub Actions", "자동화에 적합합니다.")
			),
			List.of(
				new DevGuideContent.MvpPriority(1, "로그인", "가장 먼저 필요합니다.", List.of("회원가입", "로그인", "토큰 발급")),
				new DevGuideContent.MvpPriority(2, "팀 생성", "핵심 흐름입니다.", List.of("팀 생성", "멤버 추가", "권한 설정")),
				new DevGuideContent.MvpPriority(3, "가이드 조회", "팀 생성 이후 필요합니다.",
					List.of("가이드 생성", "가이드 저장", "가이드 조회"))
			),
			List.of(
				new DevGuideContent.DecisionPoint("인증 방식", List.of("JWT", "Session"), "사용자 경험과 구현 난이도를 고려해야 합니다."),
				new DevGuideContent.DecisionPoint("팀 가입 방식", List.of("자동", "승인"), "팀 관리 정책에 영향을 줍니다."),
				new DevGuideContent.DecisionPoint("가이드 재생성 정책", List.of("제한 없음", "횟수 제한"), "API 비용에 영향을 줍니다.")
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
