package damso.school.application.port.in;

import java.util.List;
import java.util.UUID;

/** 슈퍼 관리자의 학급·학생 명단 관리 (FR-002). 담임 이름·이메일은 어떤 응답에도 넣지 않는다. */
public interface ClassroomAdminUseCase {

	/** 같은 학년·반이 이미 있으면 CONFLICT. */
	ClassroomView createClassroom(int grade, int classNumber);

	List<ClassroomView> listClassrooms();

	/** 없는 학급이면 NOT_FOUND. */
	List<StudentView> listStudents(UUID classroomId);

	/** 학생 이름을 한 번에 등록한다. 없는 학급이면 NOT_FOUND. */
	List<StudentView> registerStudents(UUID classroomId, List<String> names);

	record ClassroomView(UUID classroomId, int grade, int classNumber, boolean homeroomAssigned, long studentCount) {
	}

	record StudentView(UUID studentId, String name, boolean guardianLinked) {
	}

}
