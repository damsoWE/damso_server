package damso.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.school.application.port.in.InquiryTargetQuery;
import damso.school.application.port.in.InquiryTargetQuery.ChildTarget;
import damso.school.domain.Classroom;
import damso.school.domain.Student;
import damso.support.IntegrationTestSupport;

/** inquiry가 school을 읽는 통로. 본인 자녀·남의 자녀·담임 없음·담임 해제. */
class InquiryTargetQueryTest extends IntegrationTestSupport {

	@Autowired
	private InquiryTargetQuery query;

	@Test
	void resolvesOwnChildWithClassroomAndHomeroomTeacher() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		ChildTarget target = query.resolveChild(parent.userId(), student.getId());

		assertThat(target.studentId()).isEqualTo(student.getId());
		assertThat(target.studentName()).isEqualTo("김하나");
		assertThat(target.classroomId()).isEqualTo(classroom.getId());
		assertThat(target.homeroomTeacherId()).isEqualTo(teacher.userId());
		assertThat(target.hasHomeroomTeacher()).isTrue();
		assertThat(target.toString()).doesNotContain("김하나");
	}

	@Test
	void otherParentsChildAndUnknownStudentAreNotFound() throws Exception {
		Classroom classroom = newClassroom();
		Student mine = newStudent(classroom, "김하나");
		Student others = newStudent(classroom, "이둘");
		SignedUp parent = signUpWith(issueParentCode(mine), "박부모");
		signUpWith(issueParentCode(others), "정부모");

		assertThatThrownBy(() -> query.resolveChild(parent.userId(), others.getId()))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> query.resolveChild(parent.userId(), UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void classroomWithoutTeacherResolvesWithNullHomeroom() throws Exception {
		Classroom classroom = newClassroom();
		Student student = newStudent(classroom, "김하나");
		SignedUp parent = signUpWith(issueParentCode(student), "박부모");

		ChildTarget target = query.resolveChild(parent.userId(), student.getId());

		assertThat(target.homeroomTeacherId()).isNull();
		assertThat(target.hasHomeroomTeacher()).isFalse();
		assertThat(query.currentHomeroomTeacher(classroom.getId())).isEmpty();
	}

	@Test
	void homeroomChecksFollowTheCurrentTeacherAndReleaseEndsThem() throws Exception {
		Classroom classroom = newClassroom();
		Classroom other = newClassroom(2, 2);
		SignedUp teacher = signUpWith(issueTeacherCode(classroom), "최선생");

		assertThat(query.isCurrentHomeroomTeacher(teacher.userId(), classroom.getId())).isTrue();
		assertThat(query.isCurrentHomeroomTeacher(teacher.userId(), other.getId())).isFalse();
		assertThat(query.currentHomeroomTeacher(classroom.getId())).contains(teacher.userId());
		assertThat(query.homeroomClassroomOf(teacher.userId())).contains(classroom.getId());

		Classroom stored = classroomPort.findClassroomById(classroom.getId()).orElseThrow();
		stored.releaseTeacher();
		classroomPort.saveClassroom(stored);

		assertThat(query.isCurrentHomeroomTeacher(teacher.userId(), classroom.getId())).isFalse();
		assertThat(query.currentHomeroomTeacher(classroom.getId())).isEmpty();
		assertThat(query.homeroomClassroomOf(teacher.userId())).isEmpty();
	}

	@Test
	void studentNamesReturnsOnlyKnownStudents() {
		Classroom classroom = newClassroom();
		Student first = newStudent(classroom, "김하나");
		Student second = newStudent(classroom, "이둘");

		Map<UUID, String> names = query.studentNames(List.of(first.getId(), second.getId(), UUID.randomUUID()));

		assertThat(names).containsOnly(Map.entry(first.getId(), "김하나"), Map.entry(second.getId(), "이둘"));
		assertThat(query.studentNames(List.of())).isEmpty();
	}

}
