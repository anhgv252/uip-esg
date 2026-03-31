# Build the Flink fat-jar
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src src
RUN mvn package -DskipTests -q

# Runtime: just the fat-jar is copied to the Flink shared lib volume
FROM scratch AS export
COPY --from=build /workspace/target/uip-flink-jobs-*.jar /uip-flink-jobs.jar
