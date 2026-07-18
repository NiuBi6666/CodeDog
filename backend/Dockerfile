FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY settings.xml .
RUN mvn -s settings.xml -B -DskipTests dependency:resolve
COPY src ./src
RUN mvn -s settings.xml -B verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 1000 app && adduser -D -u 1000 -G app app
WORKDIR /app
COPY --from=build /build/target/codedog-api-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
