package damso.inquiry.adapter.in.controller.dto.response;

import java.util.UUID;

import damso.inquiry.application.port.in.InquiryStatusQuery.ClassroomInquiryStatus;

/** 학급 하나의 스레드 수(세 묶음). 학생·학부모·교사 정보, 긴급 여부, 제목·본문은 없다. */
public record ClassroomInquiryStatusResponseDTO(UUID classroomId, long waiting, long confirmed, long answered) {

	public static ClassroomInquiryStatusResponseDTO from(ClassroomInquiryStatus status) {
		return new ClassroomInquiryStatusResponseDTO(status.classroomId(), status.waiting(), status.confirmed(),
				status.answered());
	}

}
