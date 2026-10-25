# Contexto de build: la raíz del repo. El gateway no depende del JAR común.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY api-gateway api-gateway
RUN mvn -q -f api-gateway/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
