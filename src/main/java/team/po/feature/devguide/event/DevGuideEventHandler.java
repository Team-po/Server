package team.po.feature.devguide.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.feature.devguide.service.DevGuideService;
import team.po.feature.projectgroup.event.ProjectGroupCreatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevGuideEventHandler {
	private final DevGuideService devGuideService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(ProjectGroupCreatedEvent event) {
		try {
			log.info("개발 가이드라인 생성 이벤트 수신: projectGroupId={}", event.projectGroupId());

			devGuideService.generate(event.projectGroupId());

			log.info("개발 가이드라인 생성 완료: projectGroupId={}", event.projectGroupId());
		} catch (Exception e) {
			log.error("개발 가이드라인 비동기 생성 실패: projectGroupId={}", event.projectGroupId());
		}
	}
}
