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
import damso.inquiry.domain.RefinementStatus;

/**
 * 첫 열람의 "교사 확인" 전이를 자기 트랜잭션으로 수행한다: 긴급 문의는 원문 첫 열람에서 DELIVERED → TEACHER_CONFIRMED,
 * 일반 문의는 번역본 첫 열람에서 REFINING → TEACHER_CONFIRMED, 원문 확인 필요 일반 문의는 원문 첫 열람에서
 * RECEIVED → TEACHER_CONFIRMED.
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

	/**
	 * 원문 첫 열람의 "교사 확인": 긴급 문의는 DELIVERED → TEACHER_CONFIRMED, 원문 확인 필요 일반 문의는 RECEIVED → TEACHER_CONFIRMED.
	 * 그 밖(번역본이 있거나 정리 중인 일반 문의, 이미 확인된 문의)은 아무것도 하지 않는다.
	 */
	@Transactional
	void confirmOnOriginalView(UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId).orElseThrow();
		if (inquiry.isUrgent() && inquiry.getStage() == InquiryStage.DELIVERED) {
			confirm(inquiry);
			return;
		}
		if (!inquiry.isUrgent() && inquiry.getStage() == InquiryStage.RECEIVED
				&& inquiry.getRefinementStatus() == RefinementStatus.NEEDS_ORIGINAL) {
			inquiry.confirmByOriginalView();
			inquiries.saveInquiry(inquiry);
			histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.TEACHER_CONFIRMED, clock.instant()));
		}
	}

	/** 일반 문의의 번역본 첫 열람: REFINING이면 TEACHER_CONFIRMED로. 그 밖의 단계면 아무것도 하지 않는다. */
	@Transactional
	void confirmIfRefined(UUID inquiryId) {
		Inquiry inquiry = inquiries.findInquiryById(inquiryId).orElseThrow();
		if (inquiry.getStage() != InquiryStage.REFINING) {
			return;
		}
		confirm(inquiry);
	}

	private void confirm(Inquiry inquiry) {
		inquiry.moveTo(InquiryStage.TEACHER_CONFIRMED);
		inquiries.saveInquiry(inquiry);
		histories.append(InquiryStageHistory.of(inquiry.getId(), InquiryStage.TEACHER_CONFIRMED, clock.instant()));
	}

}
