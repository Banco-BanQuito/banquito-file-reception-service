FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace/Batch

COPY Batch/pom.xml ./pom.xml
COPY Batch/.mvn ./.mvn
COPY Batch/mvnw Batch/mvnw.cmd ./
RUN mvn -q -DskipTests dependency:go-offline

COPY Batch/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ENV SERVER_PORT=8084
ENV SPRING_CONFIG_IMPORT=optional:file:.env[.properties]

COPY --from=builder /workspace/Batch/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
