package damso.inquiry.domain;

/**
 * 문의 처리 단계. 허용된 전이만 가능하다.
 * 일반: RECEIVED → REFINING → TEACHER_CONFIRMED → ANSWERED
 * 긴급: RECEIVED → DELIVERED → TEACHER_CONFIRMED → ANSWERED
 * 단계는 되돌릴 수 없다. 건너뛰는 전이는 하나뿐이다: 자동 정리에 실패한(원문 확인 필요) 일반 문의는 교사가 원문을 처음 열람할 때
 * RECEIVED → TEACHER_CONFIRMED가 된다. 이 전이는 정리 상태를 아는 Inquiry.confirmByOriginalView()만 쓴다.
 * REFINING은 "정제 중"이 아니라 "정제 완료"다 — 검증을 통과한 정리 결과가 생긴 시점에 기록된다.
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

	/** 정리 단계를 건너뛰는 전이(일반 문의의 RECEIVED → TEACHER_CONFIRMED). 원문 확인 필요일 때만 쓴다. */
	public boolean canSkipRefinementTo(boolean urgent, InquiryStage next) {
		return !urgent && this == RECEIVED && next == TEACHER_CONFIRMED;
	}

}
