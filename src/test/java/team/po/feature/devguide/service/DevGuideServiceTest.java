package team.po.feature.devguide.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
	void generate_skips_whenConfirmedGuideAlreadyExists() {
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(true);

		devGuideService.generate(1L);

		verify(devGuideCommandService, never()).startInitialGeneration(any());
		verify(geminiClient, never()).generateDevGuide(any(), any());
	}

	@Test
	void generate_skips_whenAlreadyGenerating() {
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(false);
		when(devGuideCommandService.startInitialGeneration(1L)).thenReturn(false);

		devGuideService.generate(1L);

		verify(geminiClient, never()).generateDevGuide(any(), any());
	}

	@Test
	void generate_callsGeminiAndCreate_whenStartSucceeds() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(false);
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
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(false);
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

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED.getCode());

		verify(devGuideCommandService, never()).startRegeneration(any());
	}

	@Test
	void regenerate_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L))
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

		assertThatThrownBy(() -> devGuideService.regenerate(1L, 10L))
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
		when(promptBuilder.build("주제 A", "설명", "MVP")).thenReturn("prompt");
		when(geminiClient.generateDevGuide(eq("prompt"), any())).thenReturn(content);
		when(devGuideCommandService.completeRegeneration(1L, content, DevGuideGenerationType.MANUAL)).thenReturn(2);

		DevGuideRegenerateResponse result = devGuideService.regenerate(1L, 10L);

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
		when(promptBuilder.build("주제 A", "설명", "MVP")).thenReturn("prompt");
		when(geminiClient.generateDevGuide(eq("prompt"), any())).thenReturn(content);
		when(devGuideCommandService.completeRegeneration(1L, content, DevGuideGenerationType.RECOVERY)).thenReturn(3);

		DevGuideRegenerateResponse result = devGuideService.regenerate(1L, 10L);

		assertThat(result.generationType()).isEqualTo(DevGuideGenerationType.RECOVERY);
		assertThat(result.remainingRegenerationCount()).isEqualTo(3);
	}

	// ─── getDevGuide ─────────────────────────────────────────────────────────

	@Test
	void getDevGuide_returnsContentWithCompleted_whenConfirmedGuideExists() {
		DevGuideContent content = devGuideContent();
		DevGuide devGuide = DevGuide.create(projectGroup(), content, 1, DevGuideGenerationType.INITIAL, true);

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.of(devGuide));

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.generationStatus()).isEqualTo(DevGuideStatus.COMPLETED);
		assertThat(result.content()).isNotNull();
	}

	@Test
	void getDevGuide_returnsGeneratingStatus_whenGenerationInProgress() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.generationStatus()).isEqualTo(DevGuideStatus.GENERATING);
		assertThat(result.content()).isNull();
	}

	@Test
	void getDevGuide_returnsFailedStatus_whenGenerationFailed() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideGeneration generation = DevGuideGeneration.create(projectGroup);
		generation.fail();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(1L, 10L)).thenReturn(true);
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideGenerationRepository.findByProjectGroup_Id(1L)).thenReturn(Optional.of(generation));

		DevGuideQueryResponse result = devGuideService.getDevGuide(1L, 10L);

		assertThat(result.generationStatus()).isEqualTo(DevGuideStatus.FAILED);
		assertThat(result.content()).isNull();
	}

	@Test
	void getDevGuide_throwsNotFound_whenNoGenerationRecordExists() {
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