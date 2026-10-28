package damso.school.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * 가명화 사전 재료를 읽는 통로. 학급의 학생 이름·보호자·담임과 학년·반을 돌려준다.
 * 이름은 이때만 복호화한 String이며 로그·예외에 넣지 않는다 — toString은 건수만 보여 준다.
 */
public interface RosterQuery {

	record RosterStudent(UUID studentId, String name, UUID guardianId) {

		@Override
		public String toString() {
			return "RosterStudent[studentId=" + studentId + "]";
		}

	}

	/**
	 * @param homeroomTeacherId 현재 담임, 없으면 null
	 * @param teacherDisplayName 담임의 교사 프로필 표시 이름, 없으면 null
	 */
	record ClassRoster(List<RosterStudent> students, int grade, int classNumber, UUID homeroomTeacherId,
			String teacherDisplayName) {

		public ClassRoster {
			students = List.copyOf(students);
		}

		public static ClassRoster empty() {
			return new ClassRoster(List.of(), 0, 0, null, null);
		}

		@Override
		public String toString() {
			return "ClassRoster[students=" + students.size() + ", grade=" + grade + ", classNumber=" + classNumber + "]";
		}

	}

	/** 학급의 명단. 없는 학급이면 빈 명단. */
	ClassRoster rosterOf(UUID classroomId);

}
