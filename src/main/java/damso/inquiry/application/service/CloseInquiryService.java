package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.CloseInquiryUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 문의 스레드 종료 — "답변 완료" (FR-020~022). 교사의 명시적 동작으로만 전이한다(자동 전이 없음, research R2).
 * 동시 종료 요청은 {@code Inquiry}의 {@code @Version} 낙관적 락으로 하나만 성공한다 — 두 번째 저장은
 * {@code OptimisticLockingFailureException}이 나고 {@code GlobalExceptionHandler}가 CONFLICT(409)로 바꾼다.
 */
@Service
@Transactional
public class CloseInquiryService implements CloseInquiryUseCase {

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final Clock clock;

	public CloseInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			Clock clock) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.clock = clock;
	}

	@Override
	public InquiryClosedResult close(UUID teacherId, UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 종료할 수 없다(research R1).
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (inquiry.getStage() == InquiryStage.ANSWERED) {
			throw new BusinessException(ErrorCode.THREAD_CLOSED);
		}
		try {
			inquiry.moveTo(InquiryStage.ANSWERED);
		}
		catch (IllegalStateException ex) {
			throw new BusinessException(ErrorCode.INQUIRY_NOT_READY_TO_CLOSE);
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		Inquiry saved = inquiries.saveInquiry(inquiry);
		histories.append(InquiryStageHistory.of(saved.getId(), InquiryStage.ANSWERED, now));
		return new InquiryClosedResult(saved.getId(), now);
	}

}
