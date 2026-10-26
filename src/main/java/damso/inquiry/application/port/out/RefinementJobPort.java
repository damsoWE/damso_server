package damso.inquiry.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;

public interface RefinementJobPort {

	RefinementJob save(RefinementJob job);

	Optional<RefinementJob> findById(UUID id);

	Optional<RefinementJob> findByInquiryAndKind(UUID inquiryId, RefinementJobKind kind);

	/**
	 * 실행할 때가 된 작업을 짧은 트랜잭션에서 확보하고 커밋한다: (PENDING ∧ scheduled_at ≤ now) ∨ (RUNNING ∧ locked_until ≤ now)를
	 * FOR UPDATE SKIP LOCKED로 잡아 RUNNING·locked_until = now + lease로 바꾼다. 두 실행기가 같은 작업을 함께 잡지 못한다.
	 */
	List<RefinementJob> claimDue(Instant now, Duration lease, int limit);

	/**
	 * 실행할 때가 된 작업 하나만 확보한다(claimDue와 같은 조건). 안전망은 하나씩 확보해 처리한다 — 여러 개를 한꺼번에 임대하면
	 * 앞의 작업을 처리하는 동안 뒤의 임대가 끝나 다른 실행기가 같은 작업을 다시 실행할 수 있다(자체 코드 리뷰).
	 */
	Optional<RefinementJob> claimNextDue(Instant now, Duration lease);

	/** 작업 하나를 같은 조건으로 확보한다(커밋 직후 즉시 실행용). 이미 다른 곳이 잡았거나 끝났으면 비어 있다. */
	Optional<RefinementJob> claimOne(UUID jobId, Instant now, Duration lease);

}
