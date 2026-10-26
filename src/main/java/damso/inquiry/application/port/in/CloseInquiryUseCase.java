package damso.inquiry.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface CloseInquiryUseCase {

	record InquiryClosedResult(UUID inquiryId, Instant closedAt) {
	}

	/**
	 * 문의 스레드를 "답변 완료"로 종료한다. 담당 학급 교사만 할 수 있다. 자동 전이는 없다 — 교사의 명시적 동작으로만 바뀐다.
	 * {@code inquiryId}는 항상 스레드 루트여야 한다. 담당 학급이 아니거나 스레드 루트가 아니면 NOT_FOUND,
	 * 이미 종료됐으면 THREAD_CLOSED, 아직 TEACHER_CONFIRMED에 닿지 않았으면 INQUIRY_NOT_READY_TO_CLOSE.
	 */
	InquiryClosedResult close(UUID teacherId, UUID inquiryId);

}
