package damso.inquiry.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.inquiry.application.port.in.ParentInquiryQuery;
import damso.inquiry.application.port.in.StageEventView;
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

	public ParentInquiryService(InquiryPort inquiries, InquiryStageHistoryPort histories, InquiryTargetQuery targets) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.targets = targets;
	}

	@Override
	public List<InquirySummary> list(UUID parentId) {
		List<Inquiry> found = inquiries.findByParent(parentId);
		Map<UUID, String> names = targets.studentNames(found.stream().map(Inquiry::getStudentId).distinct().toList());
		return found.stream()
				.map(inquiry -> new InquirySummary(inquiry.getId(), inquiry.getStudentId(),
						names.getOrDefault(inquiry.getStudentId(), ""), inquiry.isUrgent(), inquiry.getStage(),
						inquiry.getSubmittedAt()))
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
		return new InquiryDetail(inquiry.getId(), inquiry.getStudentId(), studentName, inquiry.getContent().reveal(),
				inquiry.isUrgent(), inquiry.getStage(), inquiry.getSubmittedAt(), timeline,
				GuidancePolicy.noticesFor(inquiry.isUrgent(), inquiry.isNotificationDeferred()),
				GuidancePolicy.hotlinesFor(inquiry.isUrgent(), inquiry.getCrisisType()));
	}

}
