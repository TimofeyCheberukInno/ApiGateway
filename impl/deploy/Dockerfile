FROM maven:3.9.11-eclipse-temurin-24-alpine AS build
WORKDIR /app
COPY ./pom.xml ./pom.xml
RUN mvn dependency:go-offline
COPY ./src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
VOLUME /tmp
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]