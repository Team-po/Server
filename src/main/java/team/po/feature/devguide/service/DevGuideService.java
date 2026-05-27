package team.po.feature.devguide.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.client.GeminiClient;
import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.prompt.DevGuideSchema;
import team.po.feature.devguide.repository.DevGuideRepository;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;

@Service
@RequiredArgsConstructor
public class DevGuideService {
	private final DevGuideRepository devGuideRepository;
	private final GeminiClient geminiClient;
	private final DevGuidePromptBuilder promptBuilder;
	private final ProjectGroupRepository projectGroupRepository;
	private final DevGuideCommandService devGuideCommandService;

	// Transaction 없이 Gemini API 호출
	public void generate(Long projectGroupId) {
		if (devGuideRepository.existsByProjectGroup_Id(projectGroupId)) {
			return;
		}

		ProjectGroup projectGroup = projectGroupRepository.findById(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		String prompt = promptBuilder.build(
			projectGroup.getProjectTitle(),
			projectGroup.getProjectDescription(),
			projectGroup.getProjectMvp()
		);

		DevGuideContent content = geminiClient.generateDevGuide(
			prompt,
			DevGuideSchema.RESPONSE_SCHEMA
		);

		devGuideCommandService.create(projectGroupId, content);
	}

	public DevGuideContent getDevGuide(Long projectGroupId) {
		DevGuide devGuide = devGuideRepository.findByProjectGroup_Id(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.DEV_GUIDE_NOT_FOUND));

		return devGuide.toContent();
	}
}
