# Multi-stage Dockerfile for ticket-service (built from repo root)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY ticket-service/pom.xml ./pom.xml
COPY ticket-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
