package damso.school;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import damso.school.application.port.in.RosterQuery;
import damso.school.application.port.in.RosterQuery.ClassRoster;
import damso.school.application.port.in.RosterQuery.RosterStudent;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** F3 T012: 가명화 사전 재료(학급 명단). 학생·보호자 연결·담임 없음·없는 학급, toString에 이름 없음. */
class RosterQueryTest extends IntegrationTestSupport {

	@Autowired
	private RosterQuery query;

	@Test
	void rosterHasStudentsGuardiansHomeroomTeacherAndGradeAndClass() throws Exception {
		Classroom classroom = newClassroom(3, 2);
		Student linked = newStudent(classroom, "김민준");
		Student unlinked = newStudent(classroom, "이서연");
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(linked), "박부모");

		ClassRoster roster = query.rosterOf(classroom.getId());

		assertThat(roster.grade()).isEqualTo(3);
		assertThat(roster.classNumber()).isEqualTo(2);
		assertThat(roster.homeroomTeacherId()).isEqualTo(teacher.userId());
		assertThat(roster.teacherDisplayName()).isNotBlank();
		assertThat(roster.students()).extracting(RosterStudent::name).containsExactlyInAnyOrder("김민준", "이서연");
		assertThat(roster.students()).filteredOn(student -> student.studentId().equals(linked.getId()))
				.extracting(RosterStudent::guardianId).containsExactly(parent.userId());
		assertThat(roster.students()).filteredOn(student -> student.studentId().equals(unlinked.getId()))
				.extracting(RosterStudent::guardianId).containsOnlyNulls();
		assertThat(roster.toString()).doesNotContain("김민준").doesNotContain("이서연");
		assertThat(roster.students().get(0).toString()).doesNotContain("김민준").doesNotContain("이서연");
	}

	@Test
	void classroomWithoutTeacherHasNoTeacher() {
		Classroom classroom = newClassroom(1, 3);
		newStudent(classroom, "김민준");

		ClassRoster roster = query.rosterOf(classroom.getId());

		assertThat(roster.homeroomTeacherId()).isNull();
		assertThat(roster.teacherDisplayName()).isNull();
		assertThat(roster.students()).hasSize(1);
	}

	@Test
	void unknownClassroomIsEmpty() {
		ClassRoster roster = query.rosterOf(UUID.randomUUID());

		assertThat(roster.students()).isEmpty();
		assertThat(roster.homeroomTeacherId()).isNull();
	}

}
