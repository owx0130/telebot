FROM maven:3.9.12-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:25
WORKDIR /app

COPY --from=builder /app/target/telebot-1.0.jar telebot.jar
EXPOSE 10000
CMD ["java", "-jar", "telebot.jar"]
