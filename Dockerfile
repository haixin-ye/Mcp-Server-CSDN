FROM mcr.microsoft.com/playwright/java:v1.41.0-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
