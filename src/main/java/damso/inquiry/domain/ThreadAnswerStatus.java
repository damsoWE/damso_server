package damso.inquiry.domain;

/**
 * 교사 목록에서 스레드(최초 문의 + 재문의)를 답변 여부로 거르는 값(이슈 #28).
 * WAITING은 답변이 없거나 마지막 답변 뒤에 재문의가 온 스레드, ANSWERED는 답변이 있고 그 뒤에 재문의가 없는 스레드다.
 */
public enum ThreadAnswerStatus {

	WAITING, ANSWERED

}
