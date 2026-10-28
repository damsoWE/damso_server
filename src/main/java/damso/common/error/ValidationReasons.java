package damso.common.error;

import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;

/**
 * 검증 제약 종류 → 고정 사유 문구.
 * 사유에는 제약 조건 값(최대 길이 등)만 들어가며, 사용자가 보낸 입력값은 절대 들어가지 않는다.
 */
final class ValidationReasons {

	static final String REQUIRED = "필수 값입니다";
	static final String OUT_OF_RANGE = "허용 범위를 벗어났습니다";
	static final String BAD_FORMAT = "형식이 올바르지 않습니다";
	static final String INVALID = "올바르지 않은 값입니다";

	private static final Set<String> REQUIRED_TYPES = Set.of("NotNull", "NotBlank", "NotEmpty");
	private static final Set<String> RANGE_TYPES = Set.of("Min", "Max", "DecimalMin", "DecimalMax", "Positive",
			"PositiveOrZero", "Negative", "NegativeOrZero", "Range");
	private static final Set<String> FORMAT_TYPES = Set.of("Pattern", "Email");

	private ValidationReasons() {
	}

	static String of(ConstraintViolation<?> violation) {
		return of(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
				violation.getConstraintDescriptor().getAttributes());
	}

	static String of(String constraintType, Map<String, Object> attributes) {
		if (constraintType == null) {
			return INVALID;
		}
		if (REQUIRED_TYPES.contains(constraintType)) {
			return REQUIRED;
		}
		if ("Size".equals(constraintType) || "Length".equals(constraintType)) {
			return sizeReason(attributes);
		}
		if (RANGE_TYPES.contains(constraintType)) {
			return OUT_OF_RANGE;
		}
		if (FORMAT_TYPES.contains(constraintType)) {
			return BAD_FORMAT;
		}
		return INVALID;
	}

	private static String sizeReason(Map<String, Object> attributes) {
		int min = attributes.get("min") instanceof Integer value ? value : 0;
		int max = attributes.get("max") instanceof Integer value ? value : Integer.MAX_VALUE;
		if (min <= 0 && max != Integer.MAX_VALUE) {
			return "최대 " + max + "자입니다";
		}
		if (max == Integer.MAX_VALUE) {
			return "최소 " + min + "자입니다";
		}
		return min + "~" + max + "자여야 합니다";
	}

}
