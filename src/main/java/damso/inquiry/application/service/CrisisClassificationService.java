package damso.inquiry.application.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.in.CrisisClassificationUseCase;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.CrisisType;
import damso.inquiry.domain.Inquiry;

@Service
@Transactional
public class CrisisClassificationService implements CrisisClassificationUseCase {

	/** 받아들이는 값의 전부. 목록에 없으면 어떤 값이든 버린다. */
	private static final Map<String, CrisisType> ALLOWED = Map.of(
			"SELF_HARM", CrisisType.SELF_HARM,
			"ABUSE", CrisisType.ABUSE,
			"OTHER_SAFETY", CrisisType.OTHER_SAFETY);

	private final InquiryPort inquiries;

	public CrisisClassificationService(InquiryPort inquiries) {
		this.inquiries = inquiries;
	}

	@Override
	public void apply(UUID inquiryId, String rawType) {
		CrisisType type = rawType == null ? null : ALLOWED.get(rawType);
		if (type == null) {
			return;
		}
		Inquiry inquiry = inquiries.findInquiryById(inquiryId).orElse(null);
		if (inquiry == null || !inquiry.isUrgent()) {
			return;
		}
		if (inquiry.applyCrisisType(type)) {
			inquiries.saveInquiry(inquiry);
		}
	}

}
