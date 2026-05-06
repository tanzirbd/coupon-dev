FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -pl eureka-server -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/eureka-server/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]