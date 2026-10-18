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
		Optional<Inquiry> inquiry = inquiries.findInquiryById(inquiryId);
		if (inquiry.isEmpty() || inquiry.get().getStage() != InquiryStage.DELIVERED) {
			return false;
		}
		return targets.currentHomeroomTeacher(inquiry.get().getClassroomId()).filter(recipientId::equals).isPresent();
	}

}
