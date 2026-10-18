package damso.school.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import damso.common.error.BusinessException;
import damso.common.error.ErrorCode;
import damso.common.error.InvalidInputException;
import damso.school.application.port.in.TeacherClassroomQuery;
import damso.school.application.port.in.TeacherProfileUseCase;
import damso.school.application.port.out.ClassroomPort;
import damso.school.application.port.out.StudentPort;
import damso.school.application.port.out.TeacherProfilePort;
import damso.school.domain.Classroom;
import damso.school.domain.TeacherProfile;

@Service
@Transactional
public class TeacherService implements TeacherProfileUseCase, TeacherClassroomQuery {

	private final TeacherProfilePort profiles;

	private final ClassroomPort classrooms;

	private final StudentPort students;

	public TeacherService(TeacherProfilePort profiles, ClassroomPort classrooms, StudentPort students) {
		this.profiles = profiles;
		this.classrooms = classrooms;
		this.students = students;
	}

	@Override
	@Transactional(readOnly = true)
	public ProfileView get(UUID teacherId) {
		return toView(requireProfile(teacherId));
	}

	@Override
	public ProfileView update(UUID teacherId, String displayName, String introduction) {
		TeacherProfile profile = requireProfile(teacherId);
		try {
			profile.update(displayName, introduction);
		}
		catch (IllegalArgumentException outOfRange) {
			throw new InvalidInputException("displayName", "표시 이름 1~20자, 소개 문구 60자 이하여야 합니다");
		}
		return toView(profiles.saveProfile(profile));
	}

	@Override
	@Transactional(readOnly = true)
	public TeacherClassroomView myClassroom(UUID teacherId) {
		Classroom classroom = classrooms.findClassroomByHomeroomTeacher(teacherId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		var children = students.findStudentsByClassroom(classroom.getId()).stream()
				.map(student -> new StudentLinkView(student.getId(), student.getName().reveal(),
						student.hasGuardian()))
				.toList();
		return new TeacherClassroomView(
				new ClassroomRef(classroom.getId(), classroom.getGrade(), classroom.getClassNumber()), children);
	}

	private TeacherProfile requireProfile(UUID teacherId) {
		return profiles.findProfileByTeacher(teacherId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private static ProfileView toView(TeacherProfile profile) {
		return new ProfileView(profile.getDisplayName(), profile.getIntroduction());
	}

}
