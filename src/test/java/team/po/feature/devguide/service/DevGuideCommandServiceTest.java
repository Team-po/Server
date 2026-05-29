package team.po.feature.devguide.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGenerationType;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DevGuideCommandServiceTest {

	@Mock
	private DevGuideRepository devGuideRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@InjectMocks
	private DevGuideCommandService devGuideCommandService;

	@Test
	void create_throwsAlreadyExists_whenConfirmedDevGuideExists() {
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(true);

		assertThatThrownBy(() -> devGuideCommandService.create(1L, devGuideContent()))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.DEV_GUIDE_ALREADY_EXISTS.getCode());

		verify(projectGroupRepository, never()).findById(any());
		verify(devGuideRepository, never()).save(any());
	}

	@Test
	void create_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(false);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideCommandService.create(1L, devGuideContent()))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());

		verify(devGuideRepository, never()).save(any());
	}

	@Test
	void create_savesDevGuide_whenNoConfirmedGuideExists() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(devGuideRepository.existsByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(false);
		when(projectGroupRepository.findById(1L)).thenReturn(Optional.of(projectGroup));

		devGuideCommandService.create(1L, content);

		verify(devGuideRepository).save(argThat(devGuide ->
			devGuide.getVersionNo() == 1
				&& devGuide.isConfirmed()
		));
	}

	@Test
	void regenerate_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> devGuideCommandService.regenerate(1L, devGuideContent()))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());

		verify(devGuideRepository, never()).save(any());
	}

	@Test
	void regenerate_usesRecoveryType_whenNoConfirmedGuideExistsAfterLock() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.empty());
		when(devGuideRepository.findMaxVersionNoByProjectGroupId(1L)).thenReturn(0);

		DevGuideGenerationType result = devGuideCommandService.regenerate(1L, content);

		assertThat(result).isEqualTo(DevGuideGenerationType.RECOVERY);
		verify(devGuideRepository).save(argThat(devGuide ->
			devGuide.getVersionNo() == 1
				&& devGuide.getGenerationType() == DevGuideGenerationType.RECOVERY
				&& devGuide.isConfirmed()
		));
	}

	@Test
	void regenerate_usesManualType_andUnconfirmsOld_whenConfirmedGuideExistsAfterLock() {
		ProjectGroup projectGroup = projectGroup();
		DevGuideContent content = devGuideContent();
		DevGuide existingConfirmed = DevGuide.create(projectGroup, devGuideContent(), 1, DevGuideGenerationType.INITIAL, true);

		when(projectGroupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(projectGroup));
		when(devGuideRepository.findByProjectGroup_IdAndIsConfirmedTrue(1L)).thenReturn(Optional.of(existingConfirmed));
		when(devGuideRepository.findMaxVersionNoByProjectGroupId(1L)).thenReturn(1);

		DevGuideGenerationType result = devGuideCommandService.regenerate(1L, content);

		assertThat(result).isEqualTo(DevGuideGenerationType.MANUAL);
		assertThat(existingConfirmed.isConfirmed()).isFalse();
		verify(devGuideRepository).save(argThat(devGuide ->
			devGuide.getVersionNo() == 2
				&& devGuide.getGenerationType() == DevGuideGenerationType.MANUAL
				&& devGuide.isConfirmed()
		));
	}

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
				new DevGuideContent.MvpPriority(3, "가이드 조회", "팀 생성 이후 필요합니다.", List.of("가이드 생성", "가이드 저장", "가이드 조회"))
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
		return new DevGuideContent.Milestone(
			week,
			week + "주차 목표",
			new DevGuideContent.RoleTasks("백엔드 작업", "프론트 작업", "디자인 작업")
		);
	}
}