package damso.inquiry.application.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 긴급 문의 원문 열람 (FR-024~026). 순서가 중요하다:
 * ① 현재 담임인지 ② 긴급인지 ③ 감사 기록 ④ 첫 열람이면 단계 전이 ⑤ 본문 반환.
 * 감사 기록이 실패하면 이 트랜잭션 전체가 실패하고 본문은 나가지 않는다(원칙 III).
 * 본문은 반환값에만 있고 로그·예외에 넣지 않는다.
 */
@Service
@Transactional
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
		if (!inquiry.isUrgent()) {
			throw new BusinessException(ErrorCode.INQUIRY_CONTENT_UNAVAILABLE);
		}

		audit.record(teacherId, AuditAction.INQUIRY_CONTENT_VIEWED, AuditTargetType.INQUIRY, inquiryId);
		try {
			confirmation.confirmIfDelivered(inquiryId);
		}
		catch (OptimisticLockingFailureException ex) {
			// 다른 열람이 먼저 확인 처리했다(낙관적 락 충돌). 이미 확인된 것으로 보고 열람은 계속한다.
			// 그 밖의 데이터 접근 오류(DB 장애·이력 저장 실패)는 잡지 않는다 — 전파되어 본문이 나가지 않는다.
			log.debug("문의 확인 전이 충돌 (inquiry={}, type={})", inquiryId, ex.getClass().getSimpleName());
		}
		return new InquiryContentView(inquiryId, inquiry.getContent().reveal());
	}

}
