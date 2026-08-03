# recon-audit-starter

TICKET-ADV095 reference implementation — a Spring Boot auto-configuration
starter that wires an `AuditEventPublisher` bean automatically when this
module is added as a dependency.

## Build and install locally

```bash
cd recon-audit-starter
./mvnw clean install
jar tf target/recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports
ls -la ~/.m2/repository/com/dbtraining/reconx/recon-audit-starter/1.0.0/
```

(No `mvnw` wrapper is bundled here — copy `mvnw`/`mvnw.cmd`/`.mvn/` from the
main `reconx` project into this folder, or run with a locally installed
Maven: `mvn clean install`.)

## Add to the consumer (main reconx app)

```xml
<dependency>
    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>recon-audit-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Restart the consumer app — `AuditEventPublisher` should now appear at
`/actuator/beans`.

## Toggle off

```bash
SPRING_APPLICATION_JSON='{"reconx":{"audit":{"enabled":false}}}' ../mvnw spring-boot:run
```

The bean should disappear from `/actuator/beans` cleanly.

## Known gap (see chat discussion)

`AuditEventPublisher.publish(...)` uses Spring's in-process
`ApplicationEventPublisher` — it does not talk to Kafka. It will not
automatically feed the main app's `AuditEventConsumer`
(`@KafkaListener(topics = "trade-events")`). Bridging the two requires an
`@EventListener` in the consumer app that forwards published audit events
onto Kafka via `KafkaTemplate`, using `AuditProperties.topic` as the
destination. That bridge is not included here — ask if you want it added.
