# Build from the repository root: docker build -f gateway/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY demo-orders/pom.xml demo-orders/
COPY demo-users/pom.xml demo-users/
COPY sentinel/pom.xml sentinel/
COPY common/src common/src
COPY gateway/src gateway/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -pl gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /src/gateway/target/gateway-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
