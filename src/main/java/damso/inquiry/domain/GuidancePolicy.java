package damso.inquiry.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 학부모에게 보이는 시스템 안내를 정하는 순수 규칙. 결과는 코드뿐이고 문구는 API 계약에 고정되어 있다.
 * AI가 만든 문장이나 교사 명의의 문장은 어디에도 없다 (원칙 V, FR-016·017·019).
 */
public final class GuidancePolicy {

	private GuidancePolicy() {
	}

	/** 위기 종류별 외부 상담·신고 번호. 분류되기 전(F3 전 기본값)에는 둘 다 보여 준다. */
	public static List<HotlineCode> hotlinesFor(CrisisType crisisType) {
		return switch (crisisType) {
			case SELF_HARM -> List.of(HotlineCode.SUICIDE_PREVENTION_109);
			case ABUSE -> List.of(HotlineCode.CHILD_ABUSE_112);
			case OTHER_SAFETY -> List.of();
			case UNCLASSIFIED -> List.of(HotlineCode.CHILD_ABUSE_112, HotlineCode.SUICIDE_PREVENTION_109);
		};
	}

	/** 긴급이면 번호를 함께 보여 주고, 일반 문의에는 번호가 없다. */
	public static List<HotlineCode> hotlinesFor(boolean urgent, CrisisType crisisType) {
		return urgent ? hotlinesFor(crisisType) : List.of();
	}

	/** 일반 문의는 접수 안내(+응답 가능 시간 밖이면 지연 안내), 긴급 문의는 전달 안내만. */
	public static List<NoticeCode> noticesFor(boolean urgent, boolean deferred) {
		if (urgent) {
			return List.of(NoticeCode.URGENT_DELIVERED);
		}
		List<NoticeCode> notices = new ArrayList<>();
		notices.add(NoticeCode.RECEIVED);
		if (deferred) {
			notices.add(NoticeCode.DEFERRED_TO_AVAILABLE_HOURS);
		}
		return List.copyOf(notices);
	}

}
