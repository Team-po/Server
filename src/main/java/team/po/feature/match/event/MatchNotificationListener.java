package team.po.feature.match.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchNotificationListener {

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchCreatedEvent(MatchCreatedEvent event) {
		// log.info("매칭 알림 발송: matchingSessionId={}, targetUserCount={}",
		// 	event.matchingSessionId(), event.memberUserIds().size());
		log.debug("매칭 세션 생성 이벤트 수신: matchingSessionId={}, targetUserCount={}",
			event.matchingSessionId(), event.memberUserIds().size());

		// TODO: 푸시 알림이나 이메일 알림 연동 로직
		// for (Long userId : event.memberUserIds()) {
		// 	// 알림 서비스 호출
		// 	log.debug("매칭 알림 전송 완료: userId={}", userId);
		// }
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchAcceptedEvent(MatchAcceptedEvent event) {
		log.debug("매칭 수락 이벤트 수신: matchSessionId={}, acceptedUserId={}",
			event.matchSessionId(), event.acceptedUserId());
		// TODO: 수락 알림 (나머지 멤버 또는 이미 수락한 멤버)
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchCompletedEvent(MatchCompletedEvent event) {
		log.debug("그룹 생성 완료 이벤트 수신: matchSessionId={}, projectGroupId={}",
			event.matchSessionId(), event.projectGroupId());
		// TODO: 멤버 전체에게 그룹 생성 완료 알림
	}
}
