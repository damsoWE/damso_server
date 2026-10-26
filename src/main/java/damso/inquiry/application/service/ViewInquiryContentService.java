package damso.inquiry.application.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import damso.audit.application.port.in.AuditLogUseCase;
import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.ViewInquiryContentUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.Inquiry;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 원문 열람 (F2 FR-024~026, F3 FR-019·025 — 일반 문의도 정리 상태와 상관없이 열람할 수 있다). 순서가 중요하다:
 * ① 현재 담임인지 ② 열람 시도 기록 ③ 첫 열람이면 단계 전이(긴급, 원문 확인 필요 일반) ④ 열람 완료 기록 ⑤ 본문 반환.
 * 이 서비스는 바깥 트랜잭션을 만들지 않는다. 시도 기록은 단계 전이보다 먼저 자기 트랜잭션으로 커밋되므로,
 * 기록이 실패하면 전이도 본문 반환도 일어나지 않고(원칙 III), 전이가 일어났다면 열람 기록은 이미 남아 있다.
 * 전이가 실패해 본문이 나가지 않으면 시도 뒤에 실패 기록이 이어져, 실제 열람(완료 기록)과 구분된다.
 * 본문은 반환값에만 있고 로그·예외에 넣지 않는다.
 */
@Service
public class ViewInquiryContentService implements ViewInquiryContentUseCase {

	private static final Logger log = LoggerFactory.getLogger(ViewInquiryContentService.class);

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	private final AuditLogUseCase audit;

	private final InquiryConfirmationStep confirmation;

	public ViewInquiryContentService(InquiryPort inquiries, InquiryTargetQuery targets, AuditLogUseCase audit,
			InquiryConfirmationStep confirmation) {
		this.inquiries = inquiries;
		this.targets = targets;
		this.audit = audit;
		this.confirmation = confirmation;
	}

	@Override
	public InquiryContentView view(UUID teacherId, UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

		audit.record(teacherId, AuditAction.INQUIRY_CONTENT_VIEW_ATTEMPTED, AuditTargetType.INQUIRY, inquiryId);
		try {
			confirmation.confirmOnOriginalView(inquiryId);
		}
		catch (OptimisticLockingFailureException ex) {
			// 다른 열람이 먼저 확인 처리했다(낙관적 락 충돌). 이미 확인된 것으로 보고 열람은 계속한다.
			log.debug("문의 확인 전이 충돌 (inquiry={}, type={})", inquiryId, ex.getClass().getSimpleName());
		}
		catch (RuntimeException ex) {
			// 그 밖의 오류(DB 장애·이력 저장 실패)는 전파되어 본문이 나가지 않는다. 실패했다는 표시만 남긴다.
			recordFailure(teacherId, inquiryId, ex);
			throw ex;
		}
		audit.record(teacherId, AuditAction.INQUIRY_CONTENT_VIEWED, AuditTargetType.INQUIRY, inquiryId);
		return new InquiryContentView(inquiryId, inquiry.getContent().reveal());
	}

	private void recordFailure(UUID teacherId, UUID inquiryId, RuntimeException cause) {
		try {
			audit.record(teacherId, AuditAction.INQUIRY_CONTENT_VIEW_FAILED, AuditTargetType.INQUIRY, inquiryId);
		}
		catch (RuntimeException ex) {
			cause.addSuppressed(ex);
		}
	}

}
