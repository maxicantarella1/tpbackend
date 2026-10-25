# Contexto de build: la raíz del repo (necesita common-lib). Ver docker-compose.yml
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 1) JAR común: se instala en el .m2 del contenedor de build (igual que en local con "mvn install")
COPY common-lib common-lib
RUN mvn -q -f common-lib/pom.xml install -DskipTests
# 2) El servicio lo consume como dependencia Maven
COPY ReservaSpring ReservaSpring
RUN mvn -q -f ReservaSpring/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/ReservaSpring/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
