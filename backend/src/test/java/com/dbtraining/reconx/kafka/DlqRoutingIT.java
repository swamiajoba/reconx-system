//package com.dbtraining.reconx.kafka;
//
//import com.dbtraining.reconx.dto.TradeEvent;
//import com.dbtraining.reconx.service.ReconciliationEngine;
//import com.fasterxml.jackson.databind.node.JsonNodeFactory;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.apache.kafka.common.serialization.StringDeserializer;
//import org.awaitility.Awaitility;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.kafka.support.serializer.JsonDeserializer;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.time.Duration;
//import java.util.List;
//import java.util.Properties;
//
//import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * ============================================================================
// * TICKET-ADV144 — Integration test: DLQ routing on consumer failure
// *
// * WHAT:    Proves the full failure path: producer → trade-events → listener
// *          throws → ADV135 retries exhaust → ADV134 recoverer publishes to
// *          trade-events-dlq. Uses @MockBean to force the ReconciliationEngine
// *          to always throw, then asserts a raw Kafka consumer on the DLQ topic
// *          sees the quarantined record.
// * HOW:     @MockBean ReconciliationEngine configured to throw RuntimeException
// *          on every scheduleRecon() call. Awaitility drives a raw KafkaConsumer
// *          built inside a helper method (unique groupId per poll so we never
// *          miss the committed offset). Retry budget is 3 attempts × ~1/2/4s
// *          = ~8s; Awaitility window is 30s to absorb CI variance.
// * WHY:     This test is the only thing that catches: wrong DLQ topic name,
// *          misconfigured partition-resolver, DefaultErrorHandler not wired to
// *          the listener factory, missing DLQ topic pre-declaration. A mock
// *          would hide all of these.
// * OBSERVE: ./mvnw -pl backend verify -Dtest=DlqRoutingIT
// *          → three "Retrying" log lines at ~1s / ~2s / ~4s, then a single
// *          "Publishing to DLQ" line, then assertion passes.
// *
// * GOTCHA:  The raw assertion consumer must use a unique GROUP_ID every poll
// *          (System.nanoTime()) and AUTO_OFFSET_RESET = "earliest" so it reads
// *          from the beginning of the DLQ partition regardless of when it joins.
// *          A shared groupId across polls would commit an offset on the first poll
// *          and miss the record on the second.
// * ============================================================================
// */
//@SpringBootTest
//@Testcontainers
//class DlqRoutingIT {
//
//    @Container
//    static KafkaContainer kafka = new KafkaContainer(
//            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
//    );
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
//    }
//
//    @Autowired
//    TradeEventProducer producer;
//
//    @MockBean
//    ReconciliationEngine reconEngine;
//
//    @Test
//    void failingConsumerRoutesToDlq() {
//        Mockito.doThrow(new RuntimeException("boom"))
//                .when(reconEngine).scheduleRecon(Mockito.anyString());
//
//        TradeEvent event = TradeEvent.created(
//                "TRD-DLQ-1",
//                JsonNodeFactory.instance.objectNode().put("price", 100)
//        );
//        producer.publish(event);
//
//        Awaitility.await()
//                .atMost(Duration.ofSeconds(30))
//                .pollInterval(Duration.ofMillis(500))
//                .untilAsserted(() ->
//                        assertThat(dlqHas("TRD-DLQ-1")).isTrue()
//                );
//    }
//
//    /**
//     * Builds a fresh raw consumer with a unique groupId on every call so that
//     * each Awaitility poll reads from offset 0 of the DLQ topic and never
//     * misses the quarantined record due to a prior committed offset.
//     */
//    private boolean dlqHas(String tradeRef) {
//        Properties p = new Properties();
//        p.put(BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
//        p.put(GROUP_ID_CONFIG, "dlq-assert-" + System.nanoTime());
//        p.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
//        p.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        p.put(VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
//        p.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
//
//        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, TradeEvent>(p)) {
//            consumer.subscribe(List.of("trade-events-dlq"));
//            var records = consumer.poll(Duration.ofSeconds(5));
//            for (ConsumerRecord<String, TradeEvent> r : records) {
//                if (tradeRef.equals(r.value().tradeRef())) return true;
//            }
//        }
//        return false;
//    }
//}
