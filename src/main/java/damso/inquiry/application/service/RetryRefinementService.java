package damso.inquiry.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.RetryRefinementUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;
import damso.inquiry.domain.RefinementStatus;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 교사 재시도 (F3 US3, FR-017, research R8). 문의 1건당 1회. 작업을 다시 열고(teacher_retry_used = true) 커밋 뒤 바로 실행한다.
 * 교사 재시도는 자동 재시도 없이 한 번만 시도하고, 성공해도 알림을 다시 보내지 않는다(실행기가 처리한다).
 * 동시에 두 번 눌러도 작업 행의 @Version이 하나만 통과시킨다 — 나머지는 409.
 */
@Service
public class RetryRefinementService implements RetryRefinementUseCase {

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	private final RefinementJobPort jobs;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final TransactionTemplate transaction;

	public RetryRefinementService(InquiryPort inquiries, InquiryTargetQuery targets, RefinementJobPort jobs,
			ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
		this.inquiries = inquiries;
		this.targets = targets;
		this.jobs = jobs;
		this.events = events;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public RetryAccepted retry(UUID teacherId, UUID inquiryId) {
		try {
			return transaction.execute(status -> {
				Inquiry inquiry = inquiries.findInquiryById(inquiryId)
						.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
				if (inquiry.isUrgent() || inquiry.getRefinementStatus() != RefinementStatus.NEEDS_ORIGINAL) {
					throw new BusinessException(ErrorCode.REFINEMENT_RETRY_UNAVAILABLE);
				}
				RefinementJob job = jobs.findByInquiryAndKind(inquiryId, RefinementJobKind.REFINE)
						.filter(RefinementJob::canTeacherRetry)
						.orElseThrow(() -> new BusinessException(ErrorCode.REFINEMENT_RETRY_UNAVAILABLE));
				job.reopenForTeacherRetry(clock.instant());
				jobs.save(job);
				inquiry.reopenRefinement();
				inquiries.saveInquiry(inquiry);
				events.publishEvent(new RefinementRequested(job.getId()));
				return new RetryAccepted(inquiryId, inquiry.getRefinementStatus());
			});
		}
		catch (OptimisticLockingFailureException ex) {
			// 다른 재시도 요청이 먼저 작업을 다시 열었다
			throw new BusinessException(ErrorCode.REFINEMENT_RETRY_UNAVAILABLE);
		}
	}

}
