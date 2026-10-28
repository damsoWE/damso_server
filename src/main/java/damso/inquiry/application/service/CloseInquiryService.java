package damso.inquiry.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
 * 앱에서 쓰지 않아 운영에서는 꺼져 있다({@code damso.inquiry.close-enabled=false}, decisions.md 2026-10-27, FR-031).
 * 꺼져 있으면 문의 조회·담당 교사 확인보다 먼저 NOT_FOUND(404)다. 전이·검증은 경로를 다시 열 때를 위해 남긴다(FR-032).
 */
@Service
@Transactional
public class CloseInquiryService implements CloseInquiryUseCase {

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final Clock clock;

	private final boolean closeEnabled;

	public CloseInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			Clock clock, @Value("${damso.inquiry.close-enabled:false}") boolean closeEnabled) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.clock = clock;
		this.closeEnabled = closeEnabled;
	}

	@Override
	public InquiryClosedResult close(UUID teacherId, UUID inquiryId) {
		if (!closeEnabled) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
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
