package damso.audit.domain;

/**
 * 감사 대상 행위. F2는 긴급 문의 원문 열람만 남기고, 다른 행위는 F3~F5가 더한다.
 * 감사 기록은 append-only라 고치지 못하므로 열람 결과는 행을 더해 표시한다:
 * 시도(ATTEMPTED)가 먼저 남고, 원문이 나갔으면 VIEWED, 확인 전이가 실패해 나가지 않았으면 VIEW_FAILED가 이어진다.
 */
public enum AuditAction {

	INQUIRY_CONTENT_VIEW_ATTEMPTED,

	INQUIRY_CONTENT_VIEWED,

	INQUIRY_CONTENT_VIEW_FAILED

}
