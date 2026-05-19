FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY user-service/ user-service/
COPY course-service/ course-service/
COPY roadmap-service/ roadmap-service/
COPY api-gateway/ api-gateway/
RUN chmod +x gradlew && ./gradlew :course-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=builder /workspace/course-service/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
