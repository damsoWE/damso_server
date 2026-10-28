package damso.gateway.domain;

import java.util.List;

/**
 * 1차 가명화 사전의 재료. 정리 한 번 동안만 메모리에 있고 저장하지 않는다.
 * 학생·보호자·교사 이름과 학년·반은 호출하는 쪽(inquiry)이 채워 넘기고, 학교 용어는 게이트웨이가 자기 리소스에서 직접 채운다
 * ({@link #withSchoolTerms}) — 다른 도메인이 게이트웨이의 출력 포트를 참조하지 않게 하기 위해서다.
 * toString은 이름 대신 종류별 건수만 보여 준다.
 *
 * @param grade 학년, 모르면 0
 * @param classNumber 반, 모르면 0
 */
public record Roster(List<String> studentNames, List<String> guardianNames, List<String> teacherNames, int grade,
		int classNumber, List<String> schoolTerms) {

	public Roster {
		studentNames = List.copyOf(studentNames);
		guardianNames = List.copyOf(guardianNames);
		teacherNames = List.copyOf(teacherNames);
		schoolTerms = List.copyOf(schoolTerms);
	}

	/** 학교 용어 없이 만든다. 학교 용어는 게이트웨이 서비스가 채운다. */
	public static Roster of(List<String> studentNames, List<String> guardianNames, List<String> teacherNames, int grade,
			int classNumber) {
		return new Roster(studentNames, guardianNames, teacherNames, grade, classNumber, List.of());
	}

	public Roster withSchoolTerms(List<String> terms) {
		return new Roster(studentNames, guardianNames, teacherNames, grade, classNumber, terms);
	}

	@Override
	public String toString() {
		return "Roster[students=" + studentNames.size() + ", guardians=" + guardianNames.size() + ", teachers="
				+ teacherNames.size() + ", schoolTerms=" + schoolTerms.size() + "]";
	}

}
