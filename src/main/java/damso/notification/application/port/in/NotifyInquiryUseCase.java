package damso.notification.application.port.in;

import java.util.UUID;

/**
 * 문의가 알림 요청을 남기는 통로. 요청만 저장하며(트랜잭셔널 아웃박스) 실제 발송은 발송기가 한다.
 * 호출한 쪽의 트랜잭션에 참여하므로, 문의가 저장되면 요청도 반드시 함께 남는다.
 */
public interface NotifyInquiryUseCase {

	/** 일반 문의 알림이 어떻게 처리됐는지. */
	enum NotificationDecision {
		/** 교사가 일반 알림을 껐다. 알림 요청을 만들지 않았고 문의는 목록에 남는다. */
		OFF,
		/** 응답 가능 시간 안이라 알림 요청을 지금 시각으로 저장했다. */
		IMMEDIATE,
		/** 응답 가능 시간 밖이라 알림을 미뤘다. 교사 앞으로 대기 중인 묶음 알림이 다음 시작 시각에 잡혀 있다. */
		DEFERRED
	}

	/** 긴급 문의 알림 요청을 지금 시각으로 저장하고, 커밋 직후 즉시 발송하도록 알린다. 어떤 알림 설정에도 지연·차단되지 않는다. */
	UUID requestUrgent(UUID inquiryId, UUID teacherId);

	/**
	 * 일반 문의 알림. 교사의 알림 설정(끔·응답 가능 시간)에 따라 즉시·지연·없음 중 하나를 돌려준다.
	 * F3부터 문의는 접수 때가 아니라 정리가 확정됐을 때 이 메서드를 부른다 — 판정 기준 시각은 호출한 시각이다.
	 */
	NotificationDecision requestGeneral(UUID inquiryId, UUID teacherId);

	/**
	 * 지금 일반 문의 알림을 요청한다면 어떻게 될지 판정만 한다. 아무것도 저장하지 않는다.
	 * 접수 때 학부모에게 "응답 가능 시간에 전달됩니다"를 보일지 정하는 데 쓴다.
	 */
	NotificationDecision previewGeneral(UUID teacherId);

}
