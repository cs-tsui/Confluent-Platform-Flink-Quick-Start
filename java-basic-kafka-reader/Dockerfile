# Use the CP Flink base image
FROM confluentinc/cp-flink:1.19.1-cp1

# Set the working directory
WORKDIR /opt/flink

# Copy your custom JAR file into the image
COPY ./target/flink-kafka-reader-1.1-SNAPSHOT.jar /opt/flink/lib/flink-kafka-reader-1.1.jar

# Expose necessary ports (if any)
EXPOSE 8081

# Set the entrypoint to start Flink
# ENTRYPOINT ["/opt/flink/bin/flink", "run", "/opt/flink/lib/custom.jar"]
