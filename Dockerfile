# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Install JMeter and required tools
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Download and install JMeter
RUN wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz \
    && tar -xzf apache-jmeter-5.6.3.tgz \
    && mv apache-jmeter-5.6.3 /opt/jmeter \
    && rm apache-jmeter-5.6.3.tgz

# Create storage directory
RUN mkdir -p /var/stress-admin-storage

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/stress-admin-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8082

# Set environment variables
ENV JMETER_PATH=/opt/jmeter/bin/jmeter.sh
ENV STORAGE_PATH=/var/stress-admin-storage
ENV PORT=8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/api/usecases || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
