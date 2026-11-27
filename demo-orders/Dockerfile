# Build from the repository root: docker build -f demo-orders/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY demo-orders/pom.xml demo-orders/
COPY demo-users/pom.xml demo-users/
COPY common/src common/src
COPY demo-orders/src demo-orders/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -pl demo-orders -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /src/demo-orders/target/demo-orders-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
