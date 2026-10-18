package damso.inquiry.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.inquiry.domain.Inquiry;

public interface InquiryPort {

	/** 저장하고 바로 반영(flush)한다 — (parent_id, idempotency_key) 유니크 충돌을 호출한 곳에서 잡기 위해서다. */
	Inquiry saveInquiry(Inquiry inquiry);

	Optional<Inquiry> findInquiryById(UUID id);

	Optional<Inquiry> findByParentAndKey(UUID parentId, UUID idempotencyKey);

	/** 학부모가 작성한 문의, 최신순. */
	List<Inquiry> findByParent(UUID parentId);

	/** 학급의 문의 한 쪽. 긴급이 먼저, 그다음 최신순. page는 0부터. */
	List<Inquiry> findByClassroom(UUID classroomId, int page, int size);

	long countByClassroom(UUID classroomId);

	/** 그 교사 앞으로 알림이 미뤄졌고 아직 묶음 알림에 포함되지 않은 문의. */
	List<Inquiry> findDeferredNotNotified(UUID teacherId);

}
