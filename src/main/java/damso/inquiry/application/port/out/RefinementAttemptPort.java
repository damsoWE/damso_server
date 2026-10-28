package damso.inquiry.application.port.out;

import java.util.List;
import java.util.UUID;

import damso.inquiry.domain.RefinementAttempt;

/** 정리 시도 기록. 덧붙이기만 한다. */
public interface RefinementAttemptPort {

	RefinementAttempt append(RefinementAttempt attempt);

	List<RefinementAttempt> findByJob(UUID jobId);

}
