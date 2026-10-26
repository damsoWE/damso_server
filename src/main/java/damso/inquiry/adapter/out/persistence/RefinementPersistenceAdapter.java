package damso.inquiry.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import damso.inquiry.application.port.out.InquiryRefinementPort;
import damso.inquiry.application.port.out.RefinementAttemptPort;
import damso.inquiry.application.port.out.RefinementJobPort;
import damso.inquiry.domain.InquiryRefinement;
import damso.inquiry.domain.RefinementAttempt;
import damso.inquiry.domain.RefinementJob;
import damso.inquiry.domain.RefinementJobKind;

/** 정리 작업·시도 기록·정리 결과 포트를 Spring Data JPA로 구현한다. */
@Component
class RefinementPersistenceAdapter implements RefinementJobPort, RefinementAttemptPort, InquiryRefinementPort {

	private final SpringDataRefinementJobRepository jobs;

	private final RefinementAttemptRepository attempts;

	private final InquiryRefinementRepository refinements;

	RefinementPersistenceAdapter(SpringDataRefinementJobRepository jobs, RefinementAttemptRepository attempts,
			InquiryRefinementRepository refinements) {
		this.jobs = jobs;
		this.attempts = attempts;
		this.refinements = refinements;
	}

	// InquiryRefinementPort

	@Override
	public InquiryRefinement save(InquiryRefinement refinement) {
		return refinements.save(refinement);
	}

	@Override
	public Optional<InquiryRefinement> findByInquiry(UUID inquiryId) {
		return refinements.findByInquiryId(inquiryId);
	}

	// RefinementJobPort

	@Override
	public RefinementJob save(RefinementJob job) {
		return jobs.saveAndFlush(job);
	}

	@Override
	public Optional<RefinementJob> findById(UUID id) {
		return jobs.findById(id);
	}

	@Override
	public Optional<RefinementJob> findByInquiryAndKind(UUID inquiryId, RefinementJobKind kind) {
		return jobs.findByInquiryIdAndKind(inquiryId, kind);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<RefinementJob> claimDue(Instant now, Duration lease, int limit) {
		List<RefinementJob> due = jobs.lockDue(now, limit);
		due.forEach(job -> job.claim(now.plus(lease)));
		return jobs.saveAllAndFlush(due);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<RefinementJob> claimOne(UUID jobId, Instant now, Duration lease) {
		Optional<RefinementJob> due = jobs.lockOneDue(jobId, now);
		due.ifPresent(job -> job.claim(now.plus(lease)));
		return due.map(jobs::saveAndFlush);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<RefinementJob> claimNextDue(Instant now, Duration lease) {
		List<RefinementJob> due = jobs.lockDue(now, 1);
		due.forEach(job -> job.claim(now.plus(lease)));
		return jobs.saveAllAndFlush(due).stream().findFirst();
	}

	// RefinementAttemptPort

	@Override
	public RefinementAttempt append(RefinementAttempt attempt) {
		return attempts.save(attempt);
	}

	@Override
	public List<RefinementAttempt> findByJob(UUID jobId) {
		return attempts.findByJobIdOrderByOccurredAt(jobId);
	}

}
