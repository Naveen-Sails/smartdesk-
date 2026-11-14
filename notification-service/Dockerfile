# Multi-stage Dockerfile for notification-service
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY notification-service/pom.xml .
COPY notification-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8083
EXPOSE 8083
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
