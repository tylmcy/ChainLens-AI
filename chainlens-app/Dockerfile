FROM node:22.23.2-bookworm-slim AS web-build
WORKDIR /workspace/web
RUN npm install --global pnpm@11.9.0
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm typecheck && pnpm test && pnpm build

FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
COPY --from=web-build /workspace/web/dist /workspace/chainlens-app/src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl chainlens-app -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN groupadd --system chainlens && useradd --system --gid chainlens --home-dir /app chainlens
COPY --from=build /workspace/chainlens-app/target/chainlens-app-*.jar /app/chainlens-app.jar
COPY --from=build /workspace/docs/knowledge /app/docs/knowledge
USER chainlens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/chainlens-app.jar"]
