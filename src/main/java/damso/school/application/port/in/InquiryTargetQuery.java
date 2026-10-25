package damso.school.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * inquiry 도메인이 school 정보를 읽는 유일한 통로. 문의가 학생·학급·담임을 직접 조회하지 않는다.
 * 학생 이름은 응답용 String이며 로그에 넣지 않는다.
 */
public interface InquiryTargetQuery {

	/** 문의 대상 자녀의 접수 시점 정보. 담임이 없으면 homeroomTeacherId가 null이다. */
	record ChildTarget(UUID studentId, String studentName, UUID classroomId, UUID homeroomTeacherId) {

		public boolean hasHomeroomTeacher() {
			return homeroomTeacherId != null;
		}

		/** 학생 이름이 로그·예외에 섞이지 않도록 가린다. */
		@Override
		public String toString() {
			return "ChildTarget[studentId=" + studentId + ", classroomId=" + classroomId + "]";
		}

	}

	/** 본인이 보호자인 학생이면 대상 정보를, 아니면(없는 학생·남의 자녀) NOT_FOUND. */
	ChildTarget resolveChild(UUID parentId, UUID studentId);

	boolean isCurrentHomeroomTeacher(UUID teacherId, UUID classroomId);

	/** 학급의 현재 담임. 담임이 없거나 학급이 없으면 비어 있다. */
	Optional<UUID> currentHomeroomTeacher(UUID classroomId);

	/** 학생 UUID → 이름. 없는 학생은 키가 없다. */
	Map<UUID, String> studentNames(Collection<UUID> studentIds);

	/** 교사가 담임인 학급. 담임이 아니면 비어 있다. */
	Optional<UUID> homeroomClassroomOf(UUID teacherId);

}
