# Build
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Run (platforms set PORT; HttpPortConfiguration reads it)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/cat-rescue-api-*.jar /app/app.jar
USER spring:spring
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
