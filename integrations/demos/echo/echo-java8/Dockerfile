FROM eclipse-temurin:8-jdk AS build
WORKDIR /src
COPY EchoServer.java .
RUN javac EchoServer.java

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /src/EchoServer.class /app/
EXPOSE 8080
CMD ["java", "-cp", "/app", "EchoServer"]
