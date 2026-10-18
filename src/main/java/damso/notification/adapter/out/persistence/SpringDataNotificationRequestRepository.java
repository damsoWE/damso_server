package damso.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import damso.notification.domain.NotificationRequest;

interface SpringDataNotificationRequestRepository extends JpaRepository<NotificationRequest, UUID> {

	@Query(value = """
			select * from notification_request
			where (status = 'PENDING' and scheduled_at <= :now)
			   or (status = 'SENDING' and locked_until <= :now)
			order by scheduled_at
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<NotificationRequest> lockDue(@Param("now") Instant now, @Param("limit") int limit);

	@Query(value = """
			select * from notification_request
			where id = :id
			  and ((status = 'PENDING' and scheduled_at <= :now)
			    or (status = 'SENDING' and locked_until <= :now))
			for update skip locked
			""", nativeQuery = true)
	Optional<NotificationRequest> lockOneDue(@Param("id") UUID id, @Param("now") Instant now);

	@Modifying
	@Query(value = """
			insert into notification_request (id, created_at, updated_at, version, recipient_id, kind, status,
			    scheduled_at, attempts, active_digest_key)
			values (:id, :now, :now, 0, :teacherId, 'GENERAL_DIGEST', 'PENDING', :scheduledAt, 0, :key)
			on conflict (active_digest_key) do nothing
			""", nativeQuery = true)
	void insertDigestIfAbsent(@Param("id") UUID id, @Param("teacherId") UUID teacherId, @Param("key") String key,
			@Param("scheduledAt") Instant scheduledAt, @Param("now") Instant now);

	Optional<NotificationRequest> findFirstByRecipientIdAndKindAndStatus(UUID recipientId,
			damso.notification.domain.NotificationKind kind, damso.notification.domain.NotificationStatus status);

}
