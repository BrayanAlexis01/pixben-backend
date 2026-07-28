# Etapa 1: Compilar la aplicación
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x mvnw || true
RUN ./mvnw clean package -DskipTests

# Etapa 2: Ejecutar la aplicación
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/imagen /app/imagen

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]