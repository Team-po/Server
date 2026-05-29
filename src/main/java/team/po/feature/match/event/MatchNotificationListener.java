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

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchRejectedEvent(MatchRejectedEvent event) {
		log.debug("매칭 거절 이벤트 수신: matchSessionId={}, rejectedUserId={}",
			event.matchSessionId(), event.rejectedUserId());
		// TODO: 나머지 멤버에게 (또는 팀장에게) 거절 및 재매칭 예정 알림
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchMemberCanceledEvent(MatchMemberCanceledEvent event) {
		log.debug("매칭 취소 이벤트 수신: matchSessionId={}, canceledUserId={}",
			event.matchSessionId(), event.canceledUserId());
		// TODO: 나머지 멤버에게 (또는 팀장에게) 취소 및 재매칭 예정 알림
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchSessionDisbandedEvent(MatchSessionDisbandedEvent event) {
		log.debug("매칭 세션 해산 이벤트 수신: matchSessionId={}, restoreMemberIds={}",
			event.matchSessionId(), event.restoreMemberUserIds());
		// TODO: WAITING 복귀 멤버들에게 매칭 재진행 알림
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleMatchOrphanSessionCleanedEvent(MatchOrphanSessionCleanedEvent event) {
		log.debug("호스트 누락 세션 정리 이벤트 수신: matchSessionId={}, restoreMemberIds={}",
			event.matchSessionId(), event.restoreMemberUserIds());
		// TODO: WAITING 복귀 멤버들에게 매칭 재진행 알림
	}
}
