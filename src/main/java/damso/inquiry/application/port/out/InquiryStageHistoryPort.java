package damso.inquiry.application.port.out;

import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.InquiryStageHistory;

/** 단계 이력은 추가와 조회만 있다. 수정·삭제 메서드를 만들지 않는다. */
public interface InquiryStageHistoryPort {

	InquiryStageHistory append(InquiryStageHistory history);

	/** 시간순. 같은 시각이면 단계 진행 순서. */
	List<InquiryStageHistory> findByInquiry(UUID inquiryId);

}
