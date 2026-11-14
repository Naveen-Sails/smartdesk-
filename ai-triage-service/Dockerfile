# Multi-stage Dockerfile for ai-triage-service (built from repo root)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY ai-triage-service/pom.xml ./pom.xml
COPY ai-triage-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8084
EXPOSE 8084
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
