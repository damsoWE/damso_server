package damso.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

import damso.notification.domain.ParentNotificationSetting;

public interface ParentNotificationSettingPort {

	Optional<ParentNotificationSetting> findByParentId(UUID parentId);

	/**
	 * 없으면 insert, 있으면 update — DB 유니크 제약(parent_id)과 원자적으로 경쟁해, 같은 학부모의 동시 첫
	 * 저장 요청도 하나만 행을 만든다(PR #10 리뷰). 저장된 값을 그대로 돌려준다.
	 */
	boolean upsertAnswerNotifyEnabled(UUID parentId, boolean answerNotifyEnabled);

}
