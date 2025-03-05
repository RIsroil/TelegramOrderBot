# Use a base image with JDK 21 and Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy the Maven project file
COPY pom.xml .

# Download dependencies and plugins
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Create a lightweight image with JRE 21 and the built artifact
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/telegram-bot-0.0.1-SNAPSHOT.jar ./app.jar

# Expose the application port
EXPOSE 8100

# Command to run the application
CMD ["java", "-jar", "app.jar"]
