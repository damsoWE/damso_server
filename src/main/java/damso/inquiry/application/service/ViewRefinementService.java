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
import damso.inquiry.application.port.in.ViewRefinementUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryRefinementPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryRefinement;
import damso.inquiry.domain.InquiryStage;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 번역본 열람. ① 현재 담임인지 ② 번역본이 있는지 ③ 열람 시도 기록 ④ 첫 열람이면 "교사 확인"
 * ⑤ 열람 완료 기록 ⑥ 번역본 반환.
 * F5부터 번역본 열람도 기록한다. 기록이 실패해도 번역본은 내보내고 로그만 남긴다 — 원문과 다른 점.
 * 거부된 요청(404)과 번역본이 없는 요청(409)은 기록하지 않는다. 확인 전이가 충돌이 아닌 이유로 실패하면 실패 기록을 남기고
 * 번역본은 내보내지 않는다. 동시 첫 열람은 한쪽이 낙관적 락으로 실패하는데, 이미 확인된 것으로 보고 열람은 계속한다 —
 * 단계 이력은 한 줄만 남는다. 본문은 반환값에만 있고 로그·예외에 넣지 않는다.
 */
@Service
public class ViewRefinementService implements ViewRefinementUseCase {

	private static final Logger log = LoggerFactory.getLogger(ViewRefinementService.class);

	private final InquiryPort inquiries;

	private final InquiryRefinementPort refinements;

	private final InquiryTargetQuery targets;

	private final InquiryConfirmationStep confirmation;

	private final AuditLogUseCase audit;

	public ViewRefinementService(InquiryPort inquiries, InquiryRefinementPort refinements, InquiryTargetQuery targets,
			InquiryConfirmationStep confirmation, AuditLogUseCase audit) {
		this.inquiries = inquiries;
		this.refinements = refinements;
		this.targets = targets;
		this.confirmation = confirmation;
		this.audit = audit;
	}

	@Override
	public RefinementView view(UUID teacherId, UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		InquiryRefinement refinement = inquiry.isUrgent() ? null : refinements.findByInquiry(inquiryId).orElse(null);
		if (refinement == null || inquiry.getTitle() == null) {
			throw new BusinessException(ErrorCode.REFINEMENT_NOT_READY);
		}
		recordOrLog(teacherId, AuditAction.INQUIRY_REFINEMENT_VIEW_ATTEMPTED, inquiryId);
		if (inquiry.getStage() == InquiryStage.REFINING) {
			try {
				confirmation.confirmIfRefined(inquiryId);
			}
			catch (OptimisticLockingFailureException ex) {
				// 다른 열람이 먼저 확인 처리했다. 이미 확인된 것으로 보고 열람은 계속한다.
				log.debug("번역본 확인 전이 충돌 (inquiry={}, type={})", inquiryId, ex.getClass().getSimpleName());
			}
			catch (RuntimeException ex) {
				// 그 밖의 오류(DB 장애·이력 저장 실패)는 전파되어 번역본이 나가지 않는다. 실패했다는 표시만 남긴다.
				recordFailure(teacherId, inquiryId, ex);
				throw ex;
			}
		}
		recordOrLog(teacherId, AuditAction.INQUIRY_REFINEMENT_VIEWED, inquiryId);
		return new RefinementView(inquiryId, inquiry.getTitle().reveal(), refinement.getFacts().reveal(),
				refinement.getRequests().reveal(), refinement.getEmotionLevel(), refinement.getEmotionLabels(),
				refinement.getRefinedAt());
	}

	/** 기록이 실패해도 번역본 열람은 막지 않는다. 로그에는 식별자·행위·오류 종류만 남긴다(예외 메시지에 값이 섞일 수 있다). */
	private void recordOrLog(UUID teacherId, AuditAction action, UUID inquiryId) {
		try {
			audit.record(teacherId, action, AuditTargetType.INQUIRY, inquiryId);
		}
		catch (RuntimeException ex) {
			log.warn("번역본 열람 감사 기록 실패 (inquiry={}, action={}, type={})", inquiryId, action,
					ex.getClass().getSimpleName());
		}
	}

	private void recordFailure(UUID teacherId, UUID inquiryId, RuntimeException cause) {
		try {
			audit.record(teacherId, AuditAction.INQUIRY_REFINEMENT_VIEW_FAILED, AuditTargetType.INQUIRY, inquiryId);
		}
		catch (RuntimeException ex) {
			cause.addSuppressed(ex);
		}
	}

}
