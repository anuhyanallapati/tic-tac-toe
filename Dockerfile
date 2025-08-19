# Use OpenJDK 17
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .

# Copy source code
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven

# Build the application
RUN mvn clean compile

# Expose port
EXPOSE $PORT

# Run the application
CMD ["mvn", "exec:java", "-Dexec.mainClass=com.example.TicTacToeWebSocketServer"]
