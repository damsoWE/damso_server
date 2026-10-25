package damso.notification.domain;

/**
 * 알림 요청의 상태.
 * PENDING → SENDING(발송기가 임대) → SENT, 실패하면 SENDING → PENDING(재시도 시각 뒤로),
 * 취소·건너뜀은 끝 상태다. SENDING은 임대가 만료되면 다른 발송기가 다시 가져갈 수 있다.
 */
public enum NotificationStatus {

	PENDING,
	SENDING,
	SENT,
	CANCELLED,
	SKIPPED;

	public boolean isOpen() {
		return this == PENDING || this == SENDING;
	}

}
