package damso.inquiry.application.service;

import java.util.UUID;

/** 정리·분류 작업이 커밋됐으니 바로 실행하라는 알림. 작업 UUID만 담는다. */
record RefinementRequested(UUID jobId) {
}
