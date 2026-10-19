package damso.inquiry.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;

/**
 * 긴급 문의의 첫 열람에서 DELIVERED → TEACHER_CONFIRMED 전이를 독립 트랜잭션으로 수행한다.
 * 두 교사 요청이 동시에 처음 열람하면 한쪽은 낙관적 락(@Version)으로 실패한다. 이 단계를 따로 두면 그 실패가
 * 바깥 열람 트랜잭션(감사 기록)을 롤백으로 표시하지 않고, 단계 이력은 정확히 한 줄만 남는다.
 */
@Component
class InquiryConfirmationStep {

	private final InquiryPort inquiries;

	private final InquiryStageHistoryPort histories;

	private final Clock clock;

	InquiryConfirmationStep(InquiryPort inquiries, InquiryStageHistoryPort histories, Clock clock) {
		this.inquiries = inquiries;
		this.histories = histories;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void confirmIfDelivered(UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId).orElseThrow();
		if (inquiry.getStage() != InquiryStage.DELIVERED) {
			return;
		}
		inquiry.moveTo(InquiryStage.TEACHER_CONFIRMED);
		inquiries.saveInquiry(inquiry);
		histories.append(InquiryStageHistory.of(inquiryId, InquiryStage.TEACHER_CONFIRMED, clock.instant()));
	}

}
