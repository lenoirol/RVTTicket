# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/rvtticket-*.jar /app/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /app/app.jar app.jar
USER app
ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
# Render injects PORT; fall back to SERVER_PORT or 8080 locally.
ENTRYPOINT ["sh","-c","exec java -Dserver.port=${PORT:-${SERVER_PORT:-8080}} -jar app.jar"]
