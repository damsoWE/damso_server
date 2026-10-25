package damso.inquiry.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.in.DeferredInquiryCounter;
import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.Inquiry;

@Service
@Transactional
public class DeferredInquiryService implements DeferredInquiryCounter {

	private final InquiryPort inquiries;

	public DeferredInquiryService(InquiryPort inquiries) {
		this.inquiries = inquiries;
	}

	@Override
	public int claimDeferred(UUID teacherId, Instant now) {
		List<Inquiry> deferred = inquiries.findDeferredNotNotified(teacherId);
		Instant at = now.truncatedTo(ChronoUnit.MICROS);
		for (Inquiry inquiry : deferred) {
			inquiry.markDeferredNotified(at);
			inquiries.saveInquiry(inquiry);
		}
		return deferred.size();
	}

	@Override
	@Transactional(readOnly = true)
	public int countDeferred(UUID teacherId) {
		return inquiries.findDeferredNotNotified(teacherId).size();
	}

}
