package com.github.srcmaxim.kafka.route.events;

import org.apache.kafka.clients.consumer.ConsumerRecords;

public interface ConsumerRecordsHandler<K, V> {
    void process(ConsumerRecords<K, V> consumerRecords);
}
