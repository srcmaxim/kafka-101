package com.github.srcmaxim.kafka.route.events;

import com.github.javafaker.Faker;
import com.github.srcmaxim.kafka.event.ActingEvent;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.AvroSerde;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static io.apicurio.registry.serde.SerdeConfig.*;

public class SplitStream {

    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        Properties streamsConfiguration = new Properties(allProps);
        streamsConfiguration.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicIdStrategy.class.getName());
        streamsConfiguration.putIfAbsent(SerdeConfig.FIND_LATEST_ARTIFACT, Boolean.TRUE);
        final String inputTopic = streamsConfiguration.getProperty("input.topic.name");

        KStream<String, ActingEvent>[] branches = builder.stream(inputTopic, Consumed.with(Serdes.String(), actingSerde(allProps)))
                .branch((key, appearance) -> "drama".equals(appearance.getGenre()),
                        (key, appearance) -> "fantasy".equals(appearance.getGenre()),
                        (key, appearance) -> true);

        branches[0].to(allProps.getProperty("output.drama.topic.name"));
        branches[1].to(allProps.getProperty("output.fantasy.topic.name"));
        branches[2].to(allProps.getProperty("output.other.topic.name"));

        return builder.build();
    }

    @SneakyThrows
    private AvroSerde<ActingEvent> actingSerde(Properties allProps) {
        Map<String, Object> ordersConfig = new HashMap<>();
        ordersConfig.put(SerdeConfig.REGISTRY_URL, allProps.getProperty("schema.registry.url"));
        ordersConfig.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, true);

//        apicurio.registry.artifact-id = SimpleTopicIdStrategy
//        apicurio.registry.global-id = GetOrCreateIdStrategy // todo find alternative
//        apicurio.registry.avro-datum-provider = ReflectAvroDatumProvider
        ordersConfig.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicIdStrategy.class); // namespace: null, value: topic-value
        ordersConfig.put(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class); // for avro

        AvroSerde<ActingEvent> serde = new AvroSerde<>();
        serde.configure(ordersConfig, false);
        return serde;
    }

    public void createTopics(Properties allProps) {
        AdminClient client = AdminClient.create(allProps);

        List<NewTopic> topics = new ArrayList<>();

        topics.add(new NewTopic(
                allProps.getProperty("input.topic.name"),
                Integer.parseInt(allProps.getProperty("input.topic.partitions")),
                Short.parseShort(allProps.getProperty("input.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.drama.topic.name"),
                Integer.parseInt(allProps.getProperty("output.drama.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.drama.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.fantasy.topic.name"),
                Integer.parseInt(allProps.getProperty("output.fantasy.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.fantasy.topic.replication.factor"))));

        topics.add(new NewTopic(
                allProps.getProperty("output.other.topic.name"),
                Integer.parseInt(allProps.getProperty("output.other.topic.partitions")),
                Short.parseShort(allProps.getProperty("output.other.topic.replication.factor"))));

        client.createTopics(topics);
        client.close();
    }

    @SneakyThrows
    public void createData(Properties allProps) {
        Properties props = new Properties();
        String inputTopic = allProps.getProperty("input.topic.name");

        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, allProps.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "producer-" + inputTopic);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, allProps.getProperty("schema.registry.url"));
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicIdStrategy.class.getName());
//        mp.messaging.outgoing.movies.apicurio.registry.artifact-id=io.apicurio.registry.utils.serde.strategy.SimpleTopicIdStrategy
//        mp.messaging.outgoing.movies.apicurio.registry.global-id=io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy
//        mp.messaging.outgoing.movies.apicurio.registry.avro-datum-provider=io.apicurio.registry.utils.serde.avro.ReflectAvroDatumProvider

//        ConfigDef configDef = new ConfigDef()
//                .define(ID_HANDLER, ConfigDef.Type.CLASS, ID_HANDLER_DEFAULT, ConfigDef.Importance.MEDIUM, "TODO docs")
//                .define(ENABLE_CONFLUENT_ID_HANDLER, ConfigDef.Type.BOOLEAN, false, ConfigDef.Importance.LOW, "TODO docs")
//                .define(ENABLE_HEADERS, ConfigDef.Type.BOOLEAN, ENABLE_HEADERS_DEFAULT, ConfigDef.Importance.MEDIUM, "TODO docs")
//                .define(HEADERS_HANDLER, ConfigDef.Type.CLASS, HEADERS_HANDLER_DEFAULT, ConfigDef.Importance.MEDIUM, "TODO docs")
//                .define(USE_ID, ConfigDef.Type.STRING, USE_ID_DEFAULT, ConfigDef.Importance.MEDIUM, "TODO docs");

//        io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy
        props.putIfAbsent(AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());

        Producer<String, ActingEvent> producer = new KafkaProducer<>(props);

        String[] genres = {"drama", "fantasy", "other"};
        Faker faker = new Faker(Locale.ENGLISH, new Random(1));
        Supplier<String> genre = () -> genres[faker.random().nextInt(genres.length)];
        new Thread("fake-data-producer") {
            @Override
            @SneakyThrows
            public void run() {
                while (true) {
                    ActingEvent actingEvent = ActingEvent.newBuilder()
                            .setName(faker.artist().name())
                            .setTitle(faker.book().title())
                            .setGenre(genre.get())
                            .build();
                    producer.send(new ProducerRecord<>(inputTopic, null, actingEvent));
                    Thread.sleep(5 * 1000);
                }
            }
        }.start();
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties allProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        allProps.load(input);
        input.close();

        return allProps;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        SplitStream ss = new SplitStream();
        Properties allProps = ss.loadEnvProperties(args[0]);
        allProps.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        allProps.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        Topology topology = ss.buildTopology(allProps);
        ss.createTopics(allProps);
        ss.createData(allProps);

        final KafkaStreams streams = new KafkaStreams(topology, allProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

}