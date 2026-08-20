FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl chainlens-app -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN groupadd --system chainlens && useradd --system --gid chainlens --home-dir /app chainlens
COPY --from=build /workspace/chainlens-app/target/chainlens-app-*.jar /app/chainlens-app.jar
USER chainlens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/chainlens-app.jar"]
