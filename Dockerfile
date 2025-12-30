# Stage 1: Build Java Backend
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN cd online-compiler && mvn clean package -DskipTests

# Stage 2: Final Runtime with ALL Compilers
FROM openjdk:17-jdk-slim
WORKDIR /app

# Install all language tools locally
# Adding golang, php-cli, and mono-devel (for C#)
RUN apt-get update && apt-get install -y \
    python3 \
    g++ \
    gcc \
    nodejs \
    golang-go \
    php-cli \
    ruby \
    mono-devel \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/online-compiler/target/online-compiler-0.0.1-SNAPSHOT.jar app.jar

# Hugging Face Port
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=7860"]