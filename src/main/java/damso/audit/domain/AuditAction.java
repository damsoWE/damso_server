package damso.audit.domain;

/**
 * 감사 대상 행위. 원문·번역본 열람(시도→성공/실패)과 사람의 처리 행위(재시도 요청·점검 요청·종료)를 남긴다.
 * 시스템 행위는 inquiry_stage_history·refinement_attempt가 이미 기록한다.
 * 감사 기록은 append-only라 고치지 못하므로 열람 결과는 행을 더해 표시한다:
 * 시도(ATTEMPTED)가 먼저 남고, 내용이 나갔으면 VIEWED, 확인 전이가 실패해 나가지 않았으면 VIEW_FAILED가 이어진다.
 */
public enum AuditAction {

	INQUIRY_CONTENT_VIEW_ATTEMPTED,

	INQUIRY_CONTENT_VIEWED,

	INQUIRY_CONTENT_VIEW_FAILED,

	INQUIRY_REFINEMENT_VIEW_ATTEMPTED,

	INQUIRY_REFINEMENT_VIEWED,

	INQUIRY_REFINEMENT_VIEW_FAILED,

	REFINEMENT_RETRY_REQUESTED,

	ANSWER_REVIEW_REQUESTED,

	INQUIRY_CLOSED

}
