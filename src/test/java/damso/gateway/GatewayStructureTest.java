package damso.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import damso.gateway.application.port.out.ExternalAiPort;

/**
 * F3 T019 / 원칙 II, research R1, analyze C1: 외부 AI로 나가는 길은 게이트웨이 하나뿐이다.
 * - ExternalAiPort를 받는 클래스는 gateway.application.service에만 있다(가명화를 먼저 하는 서비스).
 * - ExternalAiPort의 운영 구현은 OpenAiClient 하나다.
 * - gateway 밖의 어떤 클래스도 gateway의 출력 포트를 참조하지 않는다(다른 도메인은 port.in으로만).
 * - gateway는 다른 도메인(inquiry·school·account·notification)을 참조하지 않는다.
 * (참조는 필드·생성자·메서드 시그니처로 본다)
 */
class GatewayStructureTest {

	private static final String GATEWAY = "damso.gateway.";

	private static final String GATEWAY_PORT_OUT = "damso.gateway.application.port.out.";

	private static List<Class<?>> mainClasses() throws ClassNotFoundException {
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(
					org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
				return true;
			}
		};
		scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile("damso\\..*")));
		List<Class<?>> classes = new ArrayList<>();
		for (var definition : scanner.findCandidateComponents("damso")) {
			Class<?> type = Class.forName(definition.getBeanClassName());
			// 테스트 클래스·테스트 지원 도구는 제외한다(가짜 외부 AI 등)
			if (type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("/test/")
					|| type.getName().startsWith("damso.support.")) {
				continue;
			}
			classes.add(type);
		}
		return classes;
	}

	private static Stream<Class<?>> referencedTypes(Class<?> type) {
		List<Class<?>> types = new ArrayList<>();
		for (Field field : type.getDeclaredFields()) {
			types.add(field.getType());
		}
		for (Constructor<?> constructor : type.getDeclaredConstructors()) {
			types.addAll(List.of(constructor.getParameterTypes()));
		}
		for (Method method : type.getDeclaredMethods()) {
			types.addAll(List.of(method.getParameterTypes()));
			types.add(method.getReturnType());
		}
		return types.stream();
	}

	@Test
	void onlyGatewayServicesReceiveTheExternalAiPort() throws Exception {
		List<String> holders = mainClasses().stream()
				.filter(type -> Stream.of(type.getDeclaredConstructors())
						.anyMatch(c -> List.of(c.getParameterTypes()).contains(ExternalAiPort.class)))
				.map(Class::getName)
				.toList();

		assertThat(holders).isNotEmpty().allMatch(name -> name.startsWith("damso.gateway.application.service."));
	}

	@Test
	void theOnlyProductionExternalAiIsOpenAiClient() throws Exception {
		List<String> implementations = mainClasses().stream()
				.filter(type -> ExternalAiPort.class.isAssignableFrom(type) && !type.isInterface()
						&& !Modifier.isAbstract(type.getModifiers()))
				.map(Class::getName)
				.toList();

		assertThat(implementations).containsExactly("damso.gateway.adapter.out.openai.OpenAiClient");
	}

	@Test
	void nothingOutsideTheGatewayTouchesItsOutputPorts() throws Exception {
		List<String> offenders = mainClasses().stream()
				.filter(type -> !type.getName().startsWith(GATEWAY))
				.filter(type -> referencedTypes(type).anyMatch(ref -> ref.getName().startsWith(GATEWAY_PORT_OUT)))
				.map(Class::getName)
				.toList();

		assertThat(offenders).isEmpty();
	}

	@Test
	void gatewayDoesNotDependOnOtherDomains() throws Exception {
		List<String> offenders = mainClasses().stream()
				.filter(type -> type.getName().startsWith(GATEWAY))
				.filter(type -> referencedTypes(type).anyMatch(ref -> Stream
						.of("damso.inquiry.", "damso.school.", "damso.account.", "damso.notification.")
						.anyMatch(ref.getName()::startsWith)))
				.map(Class::getName)
				.toList();

		assertThat(offenders).isEmpty();
	}

}
