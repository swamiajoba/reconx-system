package com.dbtraining.reconx.audit;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Thin wrapper around Spring's ApplicationEventPublisher, scoped to
 * audit events. Consumers call publish(...) instead of injecting
 * ApplicationEventPublisher directly, so the audit topic name
 * (AuditProperties.topic) stays encapsulated here rather than
 * leaking into every caller.
 */
public class AuditEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final AuditProperties props;

    public AuditEventPublisher(ApplicationEventPublisher publisher, AuditProperties props) {
        this.publisher = publisher;
        this.props = props;
    }

    public void publish(Object event) {
        publisher.publishEvent(event);
    }

    public String topic() {
        return props.getTopic();
    }
}
