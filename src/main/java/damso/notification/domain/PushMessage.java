package damso.notification.domain;

import java.util.UUID;

/**
 * 기기로 보내는 알림 내용. 제목·본문은 종류에서 정해지는 고정 문구이고, 문의 UUID(있을 때)와 묶음 건수만 담는다.
 * 학생·학부모 이름과 문의 본문은 어떤 경우에도 넣지 않는다.
 */
public record PushMessage(NotificationKind kind, UUID inquiryId, Integer digestCount) {

	public PushMessage {
		if (kind == null) {
			throw new IllegalArgumentException("kind is required");
		}
		if (kind == NotificationKind.GENERAL_DIGEST) {
			if (digestCount == null || digestCount < 1) {
				throw new IllegalArgumentException("digest count must be at least 1");
			}
			if (inquiryId != null) {
				throw new IllegalArgumentException("digest is not tied to one inquiry");
			}
		}
		else {
			if (inquiryId == null) {
				throw new IllegalArgumentException("inquiry id is required");
			}
			if (digestCount != null) {
				throw new IllegalArgumentException("digest count is only for digests");
			}
		}
	}

	public static PushMessage forInquiry(NotificationKind kind, UUID inquiryId) {
		return new PushMessage(kind, inquiryId, null);
	}

	public static PushMessage digest(int count) {
		return new PushMessage(NotificationKind.GENERAL_DIGEST, null, count);
	}

	public String title() {
		return switch (kind) {
			case URGENT -> "긴급 문의가 도착했습니다";
			case URGENT_REMINDER -> "확인하지 않은 긴급 문의가 있습니다";
			case GENERAL -> "새 문의가 도착했습니다";
			case GENERAL_DIGEST -> "쌓인 문의가 있습니다";
			case ANSWER -> "답변이 도착했습니다";
		};
	}

	public String body() {
		return switch (kind) {
			case URGENT -> "학생의 안전과 관련된 문의일 수 있습니다. 지금 확인해 주세요.";
			case URGENT_REMINDER -> "긴급 문의를 아직 확인하지 않으셨습니다. 지금 확인해 주세요.";
			case GENERAL -> "문의 목록에서 확인해 주세요.";
			case GENERAL_DIGEST -> "응답 가능 시간 밖에 접수된 문의가 " + digestCount + "건 있습니다.";
			case ANSWER -> "문의 상세에서 답변을 확인해 주세요.";
		};
	}

}
