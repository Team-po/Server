package team.po.feature.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.po.common.ai.client.GeminiClient;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.checklist.ai.ChecklistAdvicePromptBuilder;
import team.po.feature.checklist.domain.ProjectChecklist;
import team.po.feature.checklist.domain.ProjectChecklistStatus;
import team.po.feature.checklist.dto.ChecklistAiAdviceResponse;
import team.po.feature.checklist.dto.CreateProjectChecklistRequest;
import team.po.feature.checklist.dto.GenerateChecklistAdviceResponse;
import team.po.feature.checklist.dto.ProjectChecklistResponse;
import team.po.feature.checklist.dto.UpdateProjectChecklistRequest;
import team.po.feature.checklist.repository.ProjectChecklistRepository;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class ProjectChecklistServiceTest {

	@Mock
	private ProjectChecklistRepository projectChecklistRepository;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private GeminiClient geminiClient;

	private final ChecklistAdvicePromptBuilder checklistAdvicePromptBuilder = new ChecklistAdvicePromptBuilder();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private ProjectChecklistService projectChecklistService;

	@BeforeEach
		void setUp() {
			projectChecklistService = new ProjectChecklistService(
				projectChecklistRepository,
				projectGroupMemberRepository,
				checklistAdvicePromptBuilder,
				geminiClient,
				objectMapper
			);
		}

	@Test
	void getProjectChecklists_returnsChecklistWithAiAdvice() throws Exception {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = mockChecklist(projectGroup, requester);
		checklist.updateAiAdvice(objectMapper.writeValueAsString(
			new ChecklistAiAdviceResponse(
				"핵심 포인트",
				List.of("순서1", "순서2", "순서3"),
				List.of("주의1", "주의2"),
				List.of("개선1")
			)
		));

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findAllByProjectGroup_IdOrderByIdAsc(10L))
			.thenReturn(List.of(checklist));

		List<ProjectChecklistResponse> responses = projectChecklistService.getProjectChecklists(10L, requester);

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).aiAdvice()).isNotNull();
		assertThat(responses.get(0).aiAdvice().summary()).isEqualTo("핵심 포인트");
	}

	@Test
	void createProjectChecklist_savesTodoChecklist() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.save(any(ProjectChecklist.class))).thenAnswer(invocation -> {
			ProjectChecklist saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-05-17T10:00:00Z"));
			return saved;
		});

		ProjectChecklistResponse response = projectChecklistService.createProjectChecklist(
			10L,
			requester,
			new CreateProjectChecklistRequest("예외 처리 정리", "설명", LocalDate.parse("2026-05-20"), 1L)
		);

		assertThat(response.status()).isEqualTo(ProjectChecklistStatus.TODO);
		assertThat(response.aiAdvice()).isNull();
		assertThat(response.assigneeUserId()).isEqualTo(1L);
		assertThat(response.assigneeNickname()).isEqualTo("user1");
	}

	@Test
	void createProjectChecklist_throwsBadRequest_whenAssigneeIsNotProjectMember() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 99L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> projectChecklistService.createProjectChecklist(
			10L,
			requester,
			new CreateProjectChecklistRequest("예외 처리 정리", "설명", LocalDate.parse("2026-05-20"), 99L)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_CHECKLIST_ASSIGNEE_NOT_MEMBER);
	}

	@Test
	void updateProjectChecklist_clearsAiAdvice_whenDescriptionChanges() throws Exception {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = mockChecklist(projectGroup, requester);
		checklist.updateAiAdvice(objectMapper.writeValueAsString(
			new ChecklistAiAdviceResponse(
				"핵심 포인트",
				List.of("순서1", "순서2", "순서3"),
				List.of("주의1", "주의2"),
				List.of("개선1")
			)
		));

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findByIdAndProjectGroup_Id(20L, 10L))
			.thenReturn(Optional.of(checklist));

		ProjectChecklistResponse response = projectChecklistService.updateProjectChecklist(
			10L,
			20L,
			requester,
			new UpdateProjectChecklistRequest("예외 처리 정리", "새 설명", ProjectChecklistStatus.DONE, null, null)
		);

		assertThat(response.aiAdvice()).isNull();
		assertThat(checklist.getAiAdvice()).isNull();
		assertThat(response.status()).isEqualTo(ProjectChecklistStatus.DONE);
		assertThat(response.assigneeNickname()).isEqualTo("ALL");
	}

	@Test
	void updateProjectChecklist_throwsForbidden_whenProjectGroupFinished() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.FINISHED);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = mockChecklist(projectGroup, requester);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findByIdAndProjectGroup_Id(20L, 10L))
			.thenReturn(Optional.of(checklist));

		assertThatThrownBy(() -> projectChecklistService.updateProjectChecklist(
			10L,
			20L,
			requester,
			new UpdateProjectChecklistRequest("예외 처리 정리", "설명", ProjectChecklistStatus.DONE, null, null)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_CHECKLIST_WRITE_NOT_ALLOWED);
	}

	@Test
	void generateChecklistAdvice_updatesStoredAdvice() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = mockChecklist(projectGroup, requester);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findByIdAndProjectGroup_Id(20L, 10L))
			.thenReturn(Optional.of(checklist));
		when(geminiClient.generateStructuredJson(any(), any())).thenReturn("""
			{
			  "summary": "핵심 포인트",
			  "recommendedFlow": ["순서1", "순서2", "순서3"],
			  "considerations": ["주의1", "주의2"],
			  "improvementPoints": ["개선1"]
			}
			""");

		GenerateChecklistAdviceResponse response = projectChecklistService.generateChecklistAdvice(10L, 20L, requester);

		assertThat(response.aiAdvice()).isNotNull();
		assertThat(response.aiAdvice().recommendedFlow()).hasSize(3);
		assertThat(checklist.getAiAdvice()).isNotBlank();
		assertThat(response.checklistId()).isEqualTo(20L);
	}

	@Test
	void generateChecklistAdvice_throwsInternalServerError_whenGeminiResponseInvalid() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = mockChecklist(projectGroup, requester);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findByIdAndProjectGroup_Id(20L, 10L))
			.thenReturn(Optional.of(checklist));
		when(geminiClient.generateStructuredJson(any(), any())).thenReturn("""
			{
			  "summary": "핵심 포인트",
			  "recommendedFlow": ["순서1"],
			  "considerations": ["주의1", "주의2"],
			  "improvementPoints": ["개선1"]
			}
			""");

		assertThatThrownBy(() -> projectChecklistService.generateChecklistAdvice(10L, 20L, requester))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.GEMINI_INVALID_RESPONSE);
	}

	@Test
	void createProjectChecklist_throwsBadRequest_whenTitleBlankInServiceLayer() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));

		assertThatThrownBy(() -> projectChecklistService.createProjectChecklist(
			10L,
			requester,
			new CreateProjectChecklistRequest("   ", "설명", null, null)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_CHECKLIST_TITLE_REQUIRED);
	}

	@Test
	void generateChecklistAdvice_throwsBadRequest_whenDescriptionMissing() {
		Users requester = mockUser(1L);
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectChecklist checklist = ProjectChecklist.builder()
			.projectGroup(projectGroup)
			.title("예외 처리 정리")
			.description(null)
			.status(ProjectChecklistStatus.TODO)
			.createdBy(requester)
			.build();
		ReflectionTestUtils.setField(checklist, "id", 21L);
		ReflectionTestUtils.setField(checklist, "createdAt", Instant.parse("2026-05-17T10:00:00Z"));

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L))
			.thenReturn(Optional.of(member));
		when(projectChecklistRepository.findByIdAndProjectGroup_Id(21L, 10L))
			.thenReturn(Optional.of(checklist));

		assertThatThrownBy(() -> projectChecklistService.generateChecklistAdvice(10L, 21L, requester))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_CHECKLIST_DESCRIPTION_REQUIRED_FOR_AI);
		verify(geminiClient, never()).generateStructuredJson(any(), any());
	}

	private Users mockUser(Long userId) {
		Users user = Users.builder()
			.email("user" + userId + "@example.com")
			.password("encoded")
			.nickname("user" + userId)
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}

	private ProjectGroup mockProjectGroup(Long projectGroupId, ProjectGroupStatus status) {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(status)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", projectGroupId);
		return projectGroup;
	}

	private ProjectChecklist mockChecklist(ProjectGroup projectGroup, Users requester) {
		ProjectChecklist checklist = ProjectChecklist.builder()
			.projectGroup(projectGroup)
			.title("예외 처리 정리")
			.description("로그인 예외 처리를 정리한다")
			.status(ProjectChecklistStatus.TODO)
			.dueDate(LocalDate.parse("2026-05-20"))
			.assignee(requester)
			.createdBy(requester)
			.build();
		ReflectionTestUtils.setField(checklist, "id", 20L);
		ReflectionTestUtils.setField(checklist, "createdAt", Instant.parse("2026-05-17T10:00:00Z"));
		return checklist;
	}
}
