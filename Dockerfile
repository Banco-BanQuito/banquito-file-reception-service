FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV SERVER_PORT=8084
ENV SPRING_CONFIG_IMPORT=optional:file:.env[.properties]

RUN addgroup -S spring && adduser -S spring -G spring

COPY --chown=spring:spring target/*.jar app.jar

EXPOSE 8084

USER spring
ENTRYPOINT ["java", "-jar", "app.jar"]
