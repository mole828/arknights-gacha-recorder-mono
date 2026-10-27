# Build from the monorepo root: docker build -f agent/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN ./scripts/package-agent.sh

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/dist/agent/agent.jar /app/agent.jar
ENTRYPOINT ["java", "-jar", "/app/agent.jar"]
