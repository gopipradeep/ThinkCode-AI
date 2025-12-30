# Stage 1: Build the Spring Boot Application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN cd online-compiler && mvn clean package -DskipTests

# Stage 2: Runtime Environment with all Compilers
FROM openjdk:17-jdk-slim
WORKDIR /app

# Install Python, C++, and other tools directly into the image
RUN apt-get update && apt-get install -y \
    python3 \
    g++ \
    gcc \
    nodejs \
    ruby \
    && rm -rf /var/lib/apt/lists/*

# Copy the JAR from the build stage
COPY --from=build /app/online-compiler/target/online-compiler-0.0.1-SNAPSHOT.jar app.jar

# Set Port for Hugging Face (Default is 7860)
EXPOSE 7860

# Run the app and override the default port
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=7860"]