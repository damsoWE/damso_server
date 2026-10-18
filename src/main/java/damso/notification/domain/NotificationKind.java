package damso.notification.domain;

/** 알림 요청 종류. 긴급·재알림·일반은 문의 하나에, 묶음은 교사 앞으로 쌓인 일반 문의 여러 건에 대응한다. */
public enum NotificationKind {

	URGENT,
	URGENT_REMINDER,
	GENERAL,
	GENERAL_DIGEST

}
