package damso.inquiry.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.AnswerView;
import damso.inquiry.application.port.in.ParentInquiryQuery;
import damso.inquiry.application.port.in.StageEventView;
import damso.inquiry.application.port.out.AnswerPort;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.GuidancePolicy;
import damso.inquiry.domain.Inquiry;
import damso.school.application.port.in.InquiryTargetQuery;

@Service
@Transactional(readOnly = true)
public class ParentInquiryService implements ParentInquiryQuery {

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final InquiryTargetQuery targets;

	private final AnswerPort answers;

	public ParentInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets,
			AnswerPort answers) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
		this.answers = answers;
	}

	@Override
	public List<InquirySummary> list(UUID parentId) {
		List<Inquiry> found = inquiries.findByParent(parentId);
		Map<UUID, String> names = targets.studentNames(found.stream().map(Inquiry::getStudentId).distinct().toList());
		Map<UUID, Instant> lastAnswered = answers.findLastAnsweredAt(found.stream().map(Inquiry::getId).toList());
		return found.stream()
				.map(inquiry -> {
					Instant lastAnsweredAt = lastAnswered.get(inquiry.getId());
					return new InquirySummary(inquiry.getId(), inquiry.getStudentId(),
							names.getOrDefault(inquiry.getStudentId(), ""), inquiry.isUrgent(), inquiry.getStage(),
							inquiry.getSubmittedAt(), lastAnsweredAt != null, lastAnsweredAt);
				})
				.toList();
	}

	@Override
	public InquiryDetail get(UUID parentId, UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId)
				.filter(found -> parentId.equals(found.getParentId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		String studentName = targets.studentNames(List.of(inquiry.getStudentId())).getOrDefault(inquiry.getStudentId(),
				"");
		List<StageEventView> timeline = histories.findByInquiry(inquiryId).stream()
				.map(history -> new StageEventView(history.getStage(), history.getOccurredAt()))
				.toList();
		// 재문의 자신의 id로는 이 스레드 상세를 취급하지 않는다(research R1) — 스레드 루트일 때만 묶어서 보여준다.
		boolean isThreadRoot = inquiry.getThreadRootId() == null;
		List<FollowUpView> followUps = isThreadRoot ? followUpsOf(inquiry.getId()) : List.of();
		List<AnswerView> answerViews = isThreadRoot ? answersOf(inquiry.getId()) : List.of();
		return new InquiryDetail(inquiry.getId(), inquiry.getStudentId(), studentName, inquiry.getContent().reveal(),
				inquiry.isUrgent(), inquiry.getStage(), inquiry.getSubmittedAt(), timeline,
				// 접수 때 보인 안내와 같아야 한다 — 실제 알림 지연(notification_deferred)이 아니라 접수 때 판정한 값을 쓴다 (F3 R10)
				GuidancePolicy.noticesFor(inquiry.isUrgent(), inquiry.isDeferredNotice()),
				GuidancePolicy.hotlinesFor(inquiry.isUrgent(), inquiry.getCrisisType()), followUps, answerViews);
	}

	private List<FollowUpView> followUpsOf(UUID rootId) {
		return inquiries.findThreadMessages(rootId).stream()
				.filter(message -> message.getThreadRootId() != null)
				.map(message -> new FollowUpView(message.getId(), message.getContent().reveal(), message.isUrgent(),
						message.getStage(), message.getSubmittedAt(),
						GuidancePolicy.noticesFor(message.isUrgent(), message.isDeferredNotice()),
						GuidancePolicy.hotlinesFor(message.isUrgent(), message.getCrisisType())))
				.toList();
	}

	private List<AnswerView> answersOf(UUID rootId) {
		return answers.findByThreadRootId(rootId).stream()
				.map(answer -> new AnswerView(answer.getId(), answer.getContent().reveal(), answer.isReviewed(),
						answer.getSentAt()))
				.toList();
	}

}
