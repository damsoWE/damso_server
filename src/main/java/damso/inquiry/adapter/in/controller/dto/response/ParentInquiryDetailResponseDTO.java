package damso.inquiry.adapter.in.controller.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import damso.inquiry.application.port.in.ParentInquiryQuery.InquiryDetail;
import damso.inquiry.domain.HotlineCode;
import damso.inquiry.domain.InquiryStage;
import damso.inquiry.domain.NoticeCode;

/**
 * 학부모의 문의 상세. 본인이 쓴 원문과 처리 단계 타임라인, 시스템 안내 코드가 있다.
 * 안내는 코드뿐이고 고정 문구는 API 계약에 있다. 교사 이메일·이름은 어디에도 없다.
 */
public record ParentInquiryDetailResponseDTO(UUID inquiryId, UUID studentId, String studentName, String content,
		boolean urgent, InquiryStage stage, Instant submittedAt, List<StageEventResponseDTO> timeline,
		List<NoticeCode> notices, List<HotlineCode> hotlines, List<ParentFollowUpViewResponseDTO> followUps,
		List<AnswerViewResponseDTO> answers) {

	/** 받은 목록을 복사해 둔다(불변) — 만든 쪽이 나중에 목록을 바꿔도 이 값은 바뀌지 않는다. */
	public ParentInquiryDetailResponseDTO {
		timeline = timeline == null ? null : List.copyOf(timeline);
		notices = notices == null ? null : List.copyOf(notices);
		hotlines = hotlines == null ? null : List.copyOf(hotlines);
		followUps = followUps == null ? null : List.copyOf(followUps);
		answers = answers == null ? null : List.copyOf(answers);
	}

	public static ParentInquiryDetailResponseDTO from(InquiryDetail detail) {
		return new ParentInquiryDetailResponseDTO(detail.inquiryId(), detail.studentId(), detail.studentName(),
				detail.content(), detail.urgent(), detail.stage(), detail.submittedAt(),
				detail.timeline().stream().map(StageEventResponseDTO::from).toList(), detail.notices(),
				detail.hotlines(), detail.followUps().stream().map(ParentFollowUpViewResponseDTO::from).toList(),
				detail.answers().stream().map(AnswerViewResponseDTO::from).toList());
	}

	/** 본문·학생 이름이 로그에 섞이지 않도록 가린다. */
	@Override
	public String toString() {
		return "ParentInquiryDetailResponseDTO[inquiryId=" + inquiryId + "]";
	}

}
