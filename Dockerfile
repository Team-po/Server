FROM gradle:9.2.1-jdk25-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
COPY src src

RUN ./gradlew bootJar --no-daemon
RUN cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
