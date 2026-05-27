FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN javac -encoding UTF-8 -d out $(find src/main/java -name "*.java")

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PORT=8080
ENV YAZIO_DATA_DIR=/app/data
COPY --from=build /app/out ./out
COPY static ./static
VOLUME ["/app/data"]
EXPOSE 8080
CMD ["java", "-cp", "out", "de.dazw.yazio.overview.YazioOverviewApp"]
