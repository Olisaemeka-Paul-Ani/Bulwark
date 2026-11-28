# Build from the repository root: docker build -f sentinel/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY sentinel/pom.xml sentinel/
COPY demo-orders/pom.xml demo-orders/
COPY demo-users/pom.xml demo-users/
COPY common/src common/src
COPY sentinel/src sentinel/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -pl sentinel -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /src/sentinel/target/sentinel-*.jar app.jar
USER app
EXPOSE 8090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
