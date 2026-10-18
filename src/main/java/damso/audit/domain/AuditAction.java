package damso.audit.domain;

/** 감사 대상 행위. F2는 긴급 문의 원문 열람만 남기고, 다른 행위는 F3~F5가 더한다. */
public enum AuditAction {

	INQUIRY_CONTENT_VIEWED

}
