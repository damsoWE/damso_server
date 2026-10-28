package damso.inquiry.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.in.UrgentReminderPolicy;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.school.application.port.in.InquiryTargetQuery;

@Service
@Transactional(readOnly = true)
public class UrgentReminderService implements UrgentReminderPolicy {

	private final InquiryPort inquiries;

	private final InquiryTargetQuery targets;

	public UrgentReminderService(InquiryPort inquiries, InquiryTargetQuery targets) {
		this.inquiries = inquiries;
		this.targets = targets;
	}

	@Override
	public Optional<UUID> currentRecipient(UUID inquiryId) {
		return inquiries.findInquiryById(inquiryId)
				.flatMap(inquiry -> targets.currentHomeroomTeacher(inquiry.getClassroomId()));
	}

	@Override
	public boolean shouldRemind(UUID inquiryId, UUID recipientId) {
		Optional<Inquiry> found = inquiries.findInquiryById(inquiryId);
		if (found.isEmpty() || found.get().getStage() != InquiryStage.DELIVERED) {
			return false;
		}
		Inquiry inquiry = found.get();
		if (threadIsClosed(inquiry)) {
			return false;
		}
		return targets.currentHomeroomTeacher(inquiry.getClassroomId()).filter(recipientId::equals).isPresent();
	}

	/**
	 * 재문의는 자기 자신이 ANSWERED까지 가지 않는다 — 교사가 그 재문의의 원문을 끝내 열람하지
	 * 않으면 자기 단계가 DELIVERED에 머문다. 스레드 루트가 "답변 완료"로 닫혔는지도 함께 봐야 재알림이 멈춘다
	 *.
	 */
	private boolean threadIsClosed(Inquiry inquiry) {
		if (inquiry.getThreadRootId() == null) {
			return false;
		}
		return inquiries.findInquiryById(inquiry.getThreadRootId())
				.map(root -> root.getStage() == InquiryStage.ANSWERED)
				.orElse(false);
	}

}
