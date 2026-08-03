//package com.dbtraining.reconx.kafka;
//
//import com.dbtraining.reconx.dto.TradeEvent;
//import com.dbtraining.reconx.repository.AuditLogRepository;
//import com.fasterxml.jackson.databind.node.JsonNodeFactory;
//import org.awaitility.Awaitility;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.time.Duration;
//import java.util.stream.IntStream;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * ============================================================================
// * TICKET-ADV143 — Integration test: end-to-end happy path
// *
// * WHAT:    Boots a real Kafka broker in Testcontainers, publishes 100 TradeEvent
// *          records, and asserts that AuditEventConsumer (ADV132) has written
// *          exactly 100 new AuditLogEntry rows within 30 seconds.
// * HOW:     @SpringBootTest + @Testcontainers with a static KafkaContainer;
// *          @DynamicPropertySource overrides spring.kafka.bootstrap-servers so
// *          the full Spring context points at the container broker.
// *          Awaitility polls auditRepo.count() every 500ms instead of Thread.sleep.
// * WHY:     A mocked Kafka client tests your code, not your configuration.
// *          Topic name typos, wrong partition counts, missing groupId — all
// *          invisible to a mock. This test catches the bugs that only appear
// *          when bytes actually cross a network.
// * OBSERVE: ./mvnw -pl backend verify -Dtest=KafkaPipelineIT
// *          → "Tests run: 1, Failures: 0, Errors: 0" with Testcontainers log
// *          confirming the cp-kafka:7.6.0 image started on a random port.
// *
// * GOTCHA:  Assert on (before + 100), NOT on count() == 100 directly.
// *          The test database may not be empty at startup (seed data, prior tests).
// *          Asserting on the delta protects you from false failures.
// * ============================================================================
// */
//@SpringBootTest
//@Testcontainers
//class KafkaPipelineIT {
//
//    @Container
//    static KafkaContainer kafka = new KafkaContainer(
//            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
//    );
//
//    @DynamicPropertySource
//    static void kafkaProps(DynamicPropertyRegistry registry) {
//        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
//    }
//
//    @Autowired
//    TradeEventProducer producer;
//
//    @Autowired
//    AuditLogRepository auditRepo;
//
//    @Test
//    void publishesAndConsumes100Events() {
//        long before = auditRepo.count();
//
//        IntStream.range(0, 100).forEach(i ->
//                producer.publish(TradeEvent.created(
//                        "TRD-IT-" + i,
//                        JsonNodeFactory.instance.objectNode()
//                                .put("instrument", "USD/EUR")
//                                .put("price", 100 + i)
//                ))
//        );
//
//        Awaitility.await()
//                .atMost(Duration.ofSeconds(30))
//                .pollInterval(Duration.ofMillis(500))
//                .untilAsserted(() ->
//                        assertThat(auditRepo.count()).isEqualTo(before + 100)
//                );
//    }
//}
