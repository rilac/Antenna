# jar 은 CI 가 ./gradlew bootJar 로 미리 만들어 둔 것을 담기만 한다.
# 이미지 안에서 gradle 을 돌리지 않는 이유 — 러너에 JDK 21 이 이미 있고,
# 컨테이너에서 다시 빌드하면 배포할 때마다 의존성을 새로 받는다.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
