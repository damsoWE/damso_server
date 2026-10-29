package damso.inquiry.application.service;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import damso.audit.application.port.in.AuditLogUseCase;
import damso.audit.domain.AuditAction;
import damso.audit.domain.AuditTargetType;
import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.RequestAnswerReviewUseCase;
import damso.gateway.domain.Roster;
import damso.inquiry.application.port.out.AnswerReviewPort;
import damso.inquiry.application.port.out.AnswerReviewPort.ReviewContext;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryRefinementPort;
import damso.inquiry.domain.AnswerReviewResult;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryContentPolicy;
import damso.inquiry.domain.InquiryRefinement;
import damso.school.application.port.in.InquiryTargetQuery;

/**
 * 답변 점검 요청. 맥락은 교사가 답하려는 문의 하나(가장 최근 재문의, 없으면 루트)의 정리 결과뿐이다 —
 * 원문·이전 답변·다른 문의는 넘기지 않는다. 정리 결과가 없으면(긴급·정리 실패) 맥락 없이 점검한다.
 * 점검 포트가 실패하거나 예외를 던져도 이 서비스는 예외를 전파하지 않는다(fail-open).
 * 점검 요청은 감사 기록을 남긴다. 기록이 실패해도 점검 요청은 그대로 처리하고 로그만 남긴다(팀 결정 2026-10-28).
 * 바깥 트랜잭션을 두지 않는다 — 감사 기록은 자기 트랜잭션으로 커밋되고, 실패해도 이 요청을 롤백 전용으로 만들지 않는다.
 */
@Service
public class RequestAnswerReviewService implements RequestAnswerReviewUseCase {

	private static final Logger log = LoggerFactory.getLogger(RequestAnswerReviewService.class);

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	private final AnswerReviewPort review;

	private final AuditLogUseCase audit;

	private final InquiryRefinementPort refinements;

	private final RosterAssembler rosters;

	public RequestAnswerReviewService(InquiryPort inquiries, InquiryTargetQuery targets, AnswerReviewPort review,
			AuditLogUseCase audit, InquiryRefinementPort refinements, RosterAssembler rosters) {
		this.inquiries = inquiries;
		this.targets = targets;
		this.review = review;
		this.audit = audit;
		this.refinements = refinements;
		this.rosters = rosters;
	}

	@Override
	public AnswerReviewResult request(UUID teacherId, UUID inquiryId, String draftContent) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> targets.isCurrentHomeroomTeacher(teacherId, found.getClassroomId()))
				// {inquiryId}는 항상 스레드 루트다 — 재문의 자신의 id로는 점검을 요청할 수 없다.
				.filter(found -> found.getThreadRootId() == null)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		InquiryContentPolicy.validate(draftContent);
		recordOrLog(teacherId, inquiry.getId());
		try {
			AnswerReviewResult result = review.review(draftContent, contextOf(inquiry), rosterOf(teacherId, inquiry))
					.orElse(AnswerReviewResult.UNAVAILABLE);
			if (!result.available()) {
				log.info("답변 점검 결과 없음, 실패 안내로 응답 (inquiry={})", inquiry.getId());
			}
			return result;
		}
		catch (RuntimeException ex) {
			log.warn("답변 점검 실패 (inquiry={}, type={})", inquiry.getId(), ex.getClass().getSimpleName());
			return AnswerReviewResult.UNAVAILABLE;
		}
	}

	/** 답하려는 문의(가장 최근 재문의, 없으면 루트)의 정리 결과. 없으면 null. */
	private ReviewContext contextOf(Inquiry root) {
		UUID target = inquiries.findLatestFollowUpId(root.getId()).orElse(root.getId());
		return refinements.findByInquiry(target).map(RequestAnswerReviewService::contextOf).orElse(null);
	}

	private static ReviewContext contextOf(InquiryRefinement refinement) {
		return new ReviewContext(refinement.getFacts().reveal(), refinement.getRequests().reveal(),
				refinement.getEmotionLevel(), refinement.getEmotionLabels());
	}

	/** 접수 시점 학급 명단 + 학부모·접수 시점 담임·점검을 요청한 교사(정리 작업과 같은 사전). */
	private Roster rosterOf(UUID teacherId, Inquiry root) {
		Set<UUID> alsoMask = new LinkedHashSet<>();
		Stream.of(root.getParentId(), root.getTeacherId(), teacherId).filter(Objects::nonNull).forEach(alsoMask::add);
		return rosters.assemble(root.getClassroomId(), alsoMask);
	}

	/** 기록 실패로 점검 요청을 막지 않는다. 로그에는 식별자·행위·오류 종류만 남긴다(예외 메시지에 값이 섞일 수 있다). */
	private void recordOrLog(UUID teacherId, UUID inquiryId) {
		try {
			audit.record(teacherId, AuditAction.ANSWER_REVIEW_REQUESTED, AuditTargetType.INQUIRY, inquiryId);
		}
		catch (RuntimeException ex) {
			log.warn("답변 점검 요청 감사 기록 실패 (inquiry={}, action={}, type={})", inquiryId,
					AuditAction.ANSWER_REVIEW_REQUESTED, ex.getClass().getSimpleName());
		}
	}

}
