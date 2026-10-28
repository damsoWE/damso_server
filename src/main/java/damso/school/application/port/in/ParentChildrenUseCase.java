package damso.school.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 학부모의 자녀 연결. 본인이 보호자인 학생만 다룬다.
 * 담임 공개 프로필에는 표시 이름·소개 문구만 있고 이메일은 없다.
 */
public interface ParentChildrenUseCase {

	/** 본인이 보호자인 자녀들. 학급이 달라도 각각 표시한다. */
	List<ChildView> list(UUID parentId);

	/**
	 * 이미 가입한 학부모가 학부모 초대 코드를 입력해 자녀를 더 연결한다.
	 * 실패 횟수는 계정(학부모 UUID) 기준으로 센다 — 5회 실패 시 10분 잠금.
	 * 교사 코드·이미 보호자가 있는 학생의 코드·사용된 코드는 모두 같은 INVALID_INVITE_CODE다.
	 */
	ChildView link(UUID parentId, String rawInviteCode);

	record ChildView(UUID studentId, String studentName, ClassroomRef classroom, TeacherProfileView teacher) {
	}

	record ClassroomRef(UUID classroomId, int grade, int classNumber) {
	}

	/** 담임이 아직 없으면 ChildView.teacher가 null이다. */
	record TeacherProfileView(String displayName, String introduction) {
	}

}
