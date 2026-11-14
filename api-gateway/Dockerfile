# Multi-stage Dockerfile for api-gateway (built from repo root)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY api-gateway/API-Gateway/pom.xml ./pom.xml
COPY api-gateway/API-Gateway/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
