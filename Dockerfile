FROM eclipse-temurin:21.0.13.1_2-jdk

WORKDIR /app

ARG JAR_FILE=build/libs/kios-mock-pos-service-*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
