package com.github.srcmaxim.kafka.route.events;

import com.github.srcmaxim.kafka.event.City;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroSerde;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsIngest {

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        return props;
    }

    @SneakyThrows
    private AvroSerde<City> citySerde(Properties envProps) {
        Map<String, Object> ordersConfig = new HashMap<>();
        ordersConfig.put(SerdeConfig.REGISTRY_URL, envProps.getProperty("schema.registry.url"));
        ordersConfig.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, true);
        ordersConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, RecordIdStrategy.class);
        AvroSerde<City> serde = new AvroSerde<>();
        serde.configure(ordersConfig, false);
        return serde;
    }

    public Topology buildTopology(Properties envProps,
                                  final AvroSerde<City> citySerde) {
        final StreamsBuilder builder = new StreamsBuilder();

        final String inputTopic = envProps.getProperty("input.topic.name");
        final String outputTopic = envProps.getProperty("output.topic.name");

        KStream<String, City> citiesNoKey = builder.stream(inputTopic, Consumed.with(Serdes.String(), citySerde));
        final KStream<Long, City> citiesKeyed = citiesNoKey.map((k, v) -> new KeyValue<>(v.getCityId(), v));
        citiesKeyed.to(outputTopic, Produced.with(Serdes.Long(), citySerde));

        return builder.build();
    }

    public void createTopics(Properties envProps) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
        AdminClient client = AdminClient.create(config);

        List<NewTopic> topics = new ArrayList<>();
        topics.add(new NewTopic(
                envProps.getProperty("input.topic.name"),
                Integer.parseInt(envProps.getProperty("input.topic.partitions")),
                Short.parseShort(envProps.getProperty("input.topic.replication.factor"))));
        topics.add(new NewTopic(
                envProps.getProperty("output.topic.name"),
                Integer.parseInt(envProps.getProperty("output.topic.partitions")),
                Short.parseShort(envProps.getProperty("output.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "This program takes one argument: the path to an environment configuration file.");
        }

        new StreamsIngest().runRecipe(args[0]);
    }

    private void runRecipe(final String configPath) throws IOException {
        Properties envProps = loadEnvProperties(configPath);
        Properties streamProps = buildStreamsProperties(envProps);

        Topology topology = buildTopology(envProps, citySerde(envProps));
        createTopics(envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.cleanUp();
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}