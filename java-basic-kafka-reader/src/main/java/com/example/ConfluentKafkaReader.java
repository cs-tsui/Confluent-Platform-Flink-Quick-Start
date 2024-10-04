package com.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfluentKafkaReader {
    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Read Kafka credentials / configs from Kubernetes secret and config map
        Properties kafkaProps = readKafkaProperties();


        // Configure Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaProps.getProperty("bootstrap.servers"))
                .setTopics(kafkaProps.getProperty("topic-name"))
                .setGroupId(kafkaProps.getProperty("consumer-group-id"))
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperties(kafkaProps)
                .build();

        // Create a DataStream using the Kafka source
        DataStream<String> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // Print the messages to the console
        stream.print();

        // Execute the Flink job
        env.execute("Confluent Kafka Reader");
    }

    private static Properties readKafkaProperties() throws Exception {
        Properties props = new Properties();
        
        // Assuming the Kubernetes secret is mounted as files in a directory
        String secretMountPath = "/mnt/secrets/kafka/";
        
        props.put("bootstrap.servers", new String(Files.readAllBytes(Paths.get(secretMountPath + "bootstrap.servers"))));
        props.put("security.protocol", new String(Files.readAllBytes(Paths.get(secretMountPath + "security.protocol"))));
        props.put("sasl.jaas.config", new String(Files.readAllBytes(Paths.get(secretMountPath + "sasl.jaas.config"))));
        props.put("sasl.mechanism", new String(Files.readAllBytes(Paths.get(secretMountPath + "sasl.mechanism"))));
        

        String configMapMountPath = "/mnt/configmap/";
        props.put("topic-name",  new String(Files.readAllBytes(Paths.get(configMapMountPath + "topic-name"))).trim());
        props.put("consumer-group-id",  new String(Files.readAllBytes(Paths.get(configMapMountPath + "consumer-group-id"))).trim());

        System.out.println(props.toString());
        return props;
    }

    // private static String readConfigMapValue(String key) throws Exception {
    //     // Assuming the ConfigMap is mounted as files in a directory
    // }
}
