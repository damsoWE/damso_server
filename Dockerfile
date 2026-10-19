# 빌드 단계: Gradle 래퍼로 실행 가능한 jar를 만든다 (테스트는 CI에서 돌린다)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 먼저 받아 소스가 바뀌어도 캐시가 유지되게 한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*[!n].jar app.jar

# 실행 단계: JRE만, 비루트 사용자
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system damso && useradd --system --gid damso --no-create-home damso
COPY --from=build /workspace/app.jar app.jar
USER damso

EXPOSE 8080
# 시크릿은 이미지에 넣지 않는다. 모두 DAMSO_* 환경변수로 주입한다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
