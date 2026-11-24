# Build any module:  docker build --build-arg MODULE=auth-service -t vcom-store/auth-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN mvn -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
COPY --from=build /src/${MODULE}/target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
