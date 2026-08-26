FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/ksp-ge-flipper-server-1.0.0.jar /app/server.jar
COPY --from=build /root/.m2/repository/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar /app/postgresql.jar
ENV KSP_PORT=8181
EXPOSE 8181
ENTRYPOINT ["java","-cp","/app/server.jar:/app/postgresql.jar","com.ksp.geflipper.KspGeServer"]
