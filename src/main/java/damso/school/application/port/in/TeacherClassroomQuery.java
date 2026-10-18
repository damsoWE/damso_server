package damso.school.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 교사가 담당 학급의 학생별 학부모 연결 여부를 본다 (FR-035). 담임인 학급만 보이고 다른 학급은 볼 수 없다.
 * 학부모의 이메일·이름은 어떤 응답에도 없다 — 연결됨/미연결만 알려 준다 (FR-036).
 */
public interface TeacherClassroomQuery {

	/** 담임인 학급이 없으면 NOT_FOUND. */
	TeacherClassroomView myClassroom(UUID teacherId);

	record TeacherClassroomView(ClassroomRef classroom, List<StudentLinkView> students) {
	}

	record ClassroomRef(UUID classroomId, int grade, int classNumber) {
	}

	record StudentLinkView(UUID studentId, String name, boolean guardianLinked) {
	}

}
