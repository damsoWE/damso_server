package damso.inquiry.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;

interface SpringDataRefinementJobRepository extends JpaRepository<RefinementJob, UUID> {

	@Query(value = """
			select * from refinement_job
			where (status = 'PENDING' and scheduled_at <= :now)
			   or (status = 'RUNNING' and locked_until <= :now)
			order by scheduled_at
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<RefinementJob> lockDue(@Param("now") Instant now, @Param("limit") int limit);

	@Query(value = """
			select * from refinement_job
			where id = :id
			  and ((status = 'PENDING' and scheduled_at <= :now)
			    or (status = 'RUNNING' and locked_until <= :now))
			for update skip locked
			""", nativeQuery = true)
	Optional<RefinementJob> lockOneDue(@Param("id") UUID id, @Param("now") Instant now);

	Optional<RefinementJob> findByInquiryIdAndKind(UUID inquiryId, RefinementJobKind kind);

}
