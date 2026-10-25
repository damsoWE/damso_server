package damso.inquiry.domain;

/**
 * 문의 처리 단계. 허용된 전이만 가능하다.
 * 일반: RECEIVED → REFINING → TEACHER_CONFIRMED → ANSWERED
 * 긴급: RECEIVED → DELIVERED → TEACHER_CONFIRMED → ANSWERED
 * 단계는 되돌릴 수 없고 건너뛸 수 없다 (FR-021·022).
 */
public enum InquiryStage {

	RECEIVED,
	DELIVERED,
	REFINING,
	TEACHER_CONFIRMED,
	ANSWERED;

	public boolean canMoveTo(boolean urgent, InquiryStage next) {
		return switch (this) {
			case RECEIVED -> next == (urgent ? DELIVERED : REFINING);
			case DELIVERED -> urgent && next == TEACHER_CONFIRMED;
			case REFINING -> !urgent && next == TEACHER_CONFIRMED;
			case TEACHER_CONFIRMED -> next == ANSWERED;
			case ANSWERED -> false;
		};
	}

}
