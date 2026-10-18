package damso.inquiry.application.port.in;

import java.util.Optional;
import java.util.UUID;

/**
 * notification이 inquiry에 묻는 질문. 알림 서비스가 문의·학급 정보를 직접 읽지 않고 이 포트로만 묻는다.
 */
public interface UrgentReminderPolicy {

	/** 보낼 때의 받는 사람: 문의 학급의 현재 담임. 담임이 없거나 문의가 없으면 비어 있다. */
	Optional<UUID> currentRecipient(UUID inquiryId);

	/** 문의가 아직 교사에게 확인되지 않았고(DELIVERED), recipientId가 현재 담임일 때만 재알림을 보낸다. */
	boolean shouldRemind(UUID inquiryId, UUID recipientId);

}
