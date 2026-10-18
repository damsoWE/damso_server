package damso.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import damso.inquiry.application.port.out.InquiryPort;
import damso.inquiry.domain.Inquiry;
import damso.inquiry.domain.InquiryStage;
import damso.support.IntegrationTestSupport;

/** F2 T041 / FR-022, 백엔드 요건 "낙관적 락": 같은 버전에서 동시에 전이하면 한 번만 성공한다. */
class InquiryStageConcurrencyTest extends IntegrationTestSupport {

	@Autowired
	private InquiryPort inquiries;

	@Test
	void secondTransitionFromTheSameVersionIsRejected() throws Exception {
		Scenario s = teacherWithClassroom();
		UUID id = UUID.fromString(read(submitInquiry(s.parent().accessToken(), s.student().getId(), URGENT_SAMPLE),
				"$.inquiryId"));
		// 트랜잭션 밖에서 각각 읽으면 같은 버전의 서로 다른 인스턴스다
		Inquiry first = inquiries.findInquiryById(id).orElseThrow();
		Inquiry second = inquiries.findInquiryById(id).orElseThrow();
		assertThat(first).isNotSameAs(second);
		assertThat(first.getVersion()).isEqualTo(second.getVersion());

		first.moveTo(InquiryStage.TEACHER_CONFIRMED);
		inquiries.saveInquiry(first);
		second.moveTo(InquiryStage.TEACHER_CONFIRMED);

		assertThatThrownBy(() -> inquiries.saveInquiry(second)).isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(inquiries.findInquiryById(id).orElseThrow().getStage()).isEqualTo(InquiryStage.TEACHER_CONFIRMED);
	}

}
