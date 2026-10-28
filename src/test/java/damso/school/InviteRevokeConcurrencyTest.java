package damso.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import damso.school.application.port.out.InviteCodePort;
import damso.school.domain.InviteCode;
import damso.school.domain.InviteStatus;
import damso.support.IntegrationTestSupport;

/**
 * 이슈 #26: 재발급의 일괄 폐기가 version을 올려, 폐기 전에 코드를 읽어 둔 사용 처리가 저장하면서 REVOKED를 USED로 덮지 못한다
 * (유출된 코드를 재발급으로 막는 순간 그 코드로 연결이 성공하지 않는다).
 */
class InviteRevokeConcurrencyTest extends IntegrationTestSupport {

	@Autowired
	private InviteCodePort invites;

	@Autowired
	private TransactionTemplate transaction;

	@Test
	void revokeIsNotOverwrittenByAUseThatReadTheCodeBeforeIt() {
		InviteCode issued = invites.saveInvite(
				InviteCode.forTeacher(newClassroom(), "a".repeat(64), UUID.randomUUID(), clock.instant()));
		InviteCode readBeforeRevoke = invites.findInviteById(issued.getId()).orElseThrow();

		transaction.executeWithoutResult(status -> invites.revokeActiveByTargetKey(issued.getActiveTargetKey()));
		readBeforeRevoke.markUsed(UUID.randomUUID(), clock.instant());

		assertThatThrownBy(() -> invites.saveInvite(readBeforeRevoke))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(invites.findInviteById(issued.getId()).orElseThrow().getStatus()).isEqualTo(InviteStatus.REVOKED);
	}

}
