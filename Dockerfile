FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 80
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
