package damso.inquiry.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import damso.inquiry.domain.Inquiry;

interface SpringDataInquiryRepository extends JpaRepository<Inquiry, UUID> {

	Optional<Inquiry> findByParentIdAndIdempotencyKey(UUID parentId, UUID idempotencyKey);

	List<Inquiry> findByParentIdOrderBySubmittedAtDesc(UUID parentId);

	List<Inquiry> findByClassroomId(UUID classroomId, Pageable pageable);

	long countByClassroomId(UUID classroomId);

	List<Inquiry> findByTeacherIdAndNotificationDeferredTrueAndDeferredNotifiedAtIsNull(UUID teacherId);

}
