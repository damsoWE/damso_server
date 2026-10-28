package damso.support;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import damso.account.application.port.out.VerificationMailPort;

/** 테스트용 메일 포트. 보낸 코드를 이메일별로 기록해 테스트가 읽는다. 실제 발송은 하지 않는다. */
public class FakeVerificationMailPort implements VerificationMailPort {

	private final Map<String, String> signupCodes = new ConcurrentHashMap<>();

	private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

	private final Set<String> alreadyRegistered = ConcurrentHashMap.newKeySet();

	private final Map<String, Integer> sendCounts = new ConcurrentHashMap<>();

	private volatile Runnable beforeResetSend = () -> {
	};

	@Override
	public void sendSignupCode(String email, String code) {
		signupCodes.put(email, code);
		sendCounts.merge(email, 1, Integer::sum);
	}

	@Override
	public void sendAlreadyRegistered(String email) {
		alreadyRegistered.add(email);
		sendCounts.merge(email, 1, Integer::sum);
	}

	@Override
	public void sendPasswordResetCode(String email, String code) {
		beforeResetSend.run();
		resetCodes.put(email, code);
		sendCounts.merge(email, 1, Integer::sum);
	}

	public String lastSignupCode(String email) {
		return signupCodes.get(email);
	}

	public String lastResetCode(String email) {
		return resetCodes.get(email);
	}

	public boolean alreadyRegisteredSent(String email) {
		return alreadyRegistered.contains(email);
	}

	public int sendCount(String email) {
		return sendCounts.getOrDefault(email, 0);
	}

	public int totalSent() {
		return sendCounts.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** 재설정 메일을 기록하기 전에 실행할 동작(동시 발급 테스트용). clear()가 지운다. */
	public void beforeResetSend(Runnable hook) {
		this.beforeResetSend = hook;
	}

	public void clear() {
		beforeResetSend = () -> {
		};
		signupCodes.clear();
		resetCodes.clear();
		alreadyRegistered.clear();
		sendCounts.clear();
	}

}
