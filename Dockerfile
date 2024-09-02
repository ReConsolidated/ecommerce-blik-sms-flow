# Start with a base image containing Java runtime
FROM maven:3.8.4-openjdk-17-slim AS build

# Make directory /usr/src/app in Docker image
WORKDIR /usr/src/app

# Copy pom.xml and source code to the Docker image
COPY pom.xml .
COPY src ./src

# Package the application
RUN mvn clean package -DskipTests

# Start with a base image containing Java runtime
FROM openjdk:17-oracle

# Add Maintainer Info
LABEL maintainer="reconsolidated"

# Make directory /app in Docker image
WORKDIR /app

# Copy the application's jar file from the build stage image
COPY --from=build /usr/src/app/target/sms-flow-*.jar application.jar

# Run the jar file
ENTRYPOINT ["java", "-jar", "application.jar"]