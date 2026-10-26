package damso.account.adapter.in.controller.dto.response;

import damso.account.domain.Role;

/** 초대 코드가 정하는 역할만 담는다. 학급·학생 같은 연결 대상은 알려 주지 않는다. */
public record InviteCodeCheckResponseDTO(Role role) {
}
