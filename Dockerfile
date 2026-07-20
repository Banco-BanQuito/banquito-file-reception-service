FROM eclipse-temurin:21-jre
WORKDIR /app

ENV SERVER_PORT=8084
ENV SPRING_CONFIG_IMPORT=optional:file:.env[.properties]
ENV JAVA_TOOL_OPTIONS="-Dio.netty.handler.ssl.noOpenSsl=true"

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --chown=spring:spring target/*.jar app.jar

EXPOSE 8084

USER spring
ENTRYPOINT ["java", "-jar", "app.jar"]
