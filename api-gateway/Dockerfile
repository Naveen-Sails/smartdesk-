# Multi-stage Dockerfile for api-gateway
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY API-Gateway/pom.xml .
COPY API-Gateway/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
