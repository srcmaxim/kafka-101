# kafka-101

1. Start Multi Broker Kafka `docker-compose -f docker-compose-multi-broker.yml up -d`

2. Create topic

Open a shell on the broker docker container:
```
docker-compose exec broker1 bash
```
Next, create the topic that the producer can write to:
```
kafka-topics --create --topic output-topic \
    --bootstrap-server broker1:9092 \
    --replication-factor 2 --partitions 6
kafka-topics --create --topic input-topic  \
    --bootstrap-server broker1:9092 \
    --replication-factor 2 --partitions 6
```

Kafka Producer:
```
docker-compose exec broker bash
kafka-console-consumer --topic output-topic --bootstrap-server broker:9092 --from-beginning --property print.key=true --property key.separator=" : "
```

Kafka Consumer:
```
docker-compose exec broker bash
kafka-console-producer --topic input-topic --bootstrap-server broker:9092
```
    
List Topics:
```
kafka-topics --list --bootstrap-server broker:9092
```

Consume Avro (with Confluent schema-registry):
```
docker-compose exec confluent-schema-registry bash
kafka-avro-console-consumer --topic cities \
   --bootstrap-server broker:9092 --from-beginning \
   --property schema.registry.url=http://localhost:8080 \
   --property print.key=true \
   --property key.deserializer=org.apache.kafka.common.serialization.LongDeserializer \
   --timeout-ms 20000
kafka-avro-console-consumer --topic cities_keyed \
   --bootstrap-server broker:9092 --from-beginning \
   --property schema.registry.url=http://localhost:8080 \
   --property print.key=true \
   --property key.deserializer=org.apache.kafka.common.serialization.LongDeserializer \
   --timeout-ms 20000
kafka-avro-console-consumer --topic key_cities \
   --bootstrap-server broker:9092 --from-beginning \
   --property schema.registry.url=http://localhost:8080 \
   --property print.key=true \
   --property key.deserializer=org.apache.kafka.common.serialization.LongDeserializer \
   --timeout-ms 20000
docker exec -i confluent-schema-registry /usr/bin/kafka-avro-console-producer \
    --topic acting-events \
    --bootstrap-server broker:9092 \
    --property value.schema="$(< domain/avro-events/src/main/avro/acting_event.avsc)"
    
docker exec -i confluent-schema-registry /usr/bin/kafka-avro-console-consumer \
    --topic acting-events \
    --bootstrap-server broker:9092 \
    --property value.schema="$(< domain/avro-events/src/main/avro/acting_event.avsc)"
```

Schema Registry get schemas:
```
curl -X GET http://localhost:8080/subjects
curl -X GET http://localhost:8080/subjects/cities-value/versions
curl -X GET http://localhost:8080/subjects/cities-value/versions/1
curl -X GET -H "Content-Type:application/vnd.schemaregistry.v1+json" \
   http://localhost:8080/schemas/ids/1?fetchMaxId=false
```
