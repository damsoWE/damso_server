package damso.inquiry.application.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

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
 * 번역본 열람 (F3 US2, FR-025·026). ① 현재 담임인지 ② 번역본이 있는지 ③ 첫 열람이면 "교사 확인" ④ 번역본 반환.
 * 번역본 열람은 감사 기록 대상이 아니다(원문 열람만 기록한다). 동시 첫 열람은 한쪽이 낙관적 락으로 실패하는데, 이미 확인된 것으로 보고
 * 열람은 계속한다 — 단계 이력은 한 줄만 남는다. 본문은 반환값에만 있고 로그·예외에 넣지 않는다.
 */
@Service
public class ViewRefinementService implements ViewRefinementUseCase {

	private static final Logger log = LoggerFactory.getLogger(ViewRefinementService.class);

	private final InquiryPort inquiries;

	private final InquiryRefinementPort refinements;

	private final InquiryTargetQuery targets;

	private final InquiryConfirmationStep confirmation;

	public ViewRefinementService(InquiryPort inquiries, InquiryRefinementPort refinements, InquiryTargetQuery targets,
			InquiryConfirmationStep confirmation) {
		this.inquiries = inquiries;
		this.refinements = refinements;
		this.targets = targets;
		this.confirmation = confirmation;
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
		if (inquiry.getStage() == InquiryStage.REFINING) {
			try {
				confirmation.confirmIfRefined(inquiryId);
			}
			catch (OptimisticLockingFailureException ex) {
				// 다른 열람이 먼저 확인 처리했다. 이미 확인된 것으로 보고 열람은 계속한다.
				log.debug("번역본 확인 전이 충돌 (inquiry={}, type={})", inquiryId, ex.getClass().getSimpleName());
			}
		}
		return new RefinementView(inquiryId, inquiry.getTitle().reveal(), refinement.getFacts().reveal(),
				refinement.getRequests().reveal(), refinement.getEmotionLevel(), refinement.getEmotionLabels(),
				refinement.getRefinedAt());
	}

}
