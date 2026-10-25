package damso.inquiry.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.application.port.out.InquiryStageHistoryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.InquiryStageHistory;

/**
 * 긴급 문의의 첫 열람에서 DELIVERED → TEACHER_CONFIRMED 전이를 자기 트랜잭션으로 수행한다.
 * 두 교사 요청이 동시에 처음 열람하면 한쪽은 낙관적 락(@Version)으로 실패한다. 전이와 단계 이력이 한 트랜잭션이라
 * 단계 이력은 정확히 한 줄만 남는다. 감사 기록은 호출 전에 이미 커밋돼 있다.
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

	@Transactional
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
