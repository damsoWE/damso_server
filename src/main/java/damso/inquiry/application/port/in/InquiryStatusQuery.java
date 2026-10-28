package damso.inquiry.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 슈퍼 관리자 문의 현황. 학급마다 스레드(루트 문의) 수를 세 묶음으로만 돌려준다.
 * 긴급 여부·원문 확인 필요 여부·학생 단위 정보는 드러나지 않는다.
 */
public interface InquiryStatusQuery {

	/** 관리자 학급 목록과 같은 학급들을 학년·반 순으로. 문의가 없는 학급도 0으로 나온다. */
	List<ClassroomInquiryStatus> statusByClassroom();

	/**
	 * waiting: 답변 0건이고 교사 확인 전(RECEIVED·DELIVERED·REFINING). confirmed: 답변 0건이고 TEACHER_CONFIRMED.
	 * answered: 답변 1건 이상 또는 ANSWERED — 먼저 판정한다(답변은 교사 확인 전에도 보낼 수 있다).
	 */
	record ClassroomInquiryStatus(UUID classroomId, long waiting, long confirmed, long answered) {
	}

}
