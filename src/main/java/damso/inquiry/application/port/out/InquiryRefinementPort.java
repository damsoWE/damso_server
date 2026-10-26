package damso.inquiry.application.port.out;

import java.util.Optional;
import java.util.UUID;

import damso.inquiry.domain.InquiryRefinement;

/** 정리 결과(번역본). 덧붙이기만 한다(원칙 III). 정리 결과로 조회하는 메서드는 두지 않는다 — 문의 UUID로만 찾는다(FR-010). */
public interface InquiryRefinementPort {

	InquiryRefinement save(InquiryRefinement refinement);

	Optional<InquiryRefinement> findByInquiry(UUID inquiryId);

}
