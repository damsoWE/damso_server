package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.ViewRefinementUseCase.RefinementView;

/** 번역본. 이름·학급은 원래 값으로 되돌려져 있다. toString은 본문을 가려 로그에 섞이지 않게 한다. */
public record InquiryRefinementResponseDTO(UUID inquiryId, String title, String facts, String requests,
		String emotionLevel, List<String> emotionLabels, Instant refinedAt) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public InquiryRefinementResponseDTO {
		emotionLabels = emotionLabels == null ? null : List.copyOf(emotionLabels);
	}

	public static InquiryRefinementResponseDTO from(RefinementView view) {
		return new InquiryRefinementResponseDTO(view.inquiryId(), view.title(), view.facts(), view.requests(),
				view.emotionLevel(), view.emotionLabels(), view.refinedAt());
	}

	@Override
	public String toString() {
		return "InquiryRefinementResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
