package com.tum.user.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Prometheus counters for authentication activity, scraped from
 * /actuator/prometheus and visualised on the Grafana "Auth &amp; Security" dashboard.
 */
@Component
public class AuthMetrics {

    private final Counter signups;
    private final Counter loginsSuccess;
    private final Counter loginsFailure;
    private final Counter logouts;

    public AuthMetrics(MeterRegistry registry) {
        this.signups = Counter.builder("auth.signups").register(registry);
        this.loginsSuccess = Counter.builder("auth.logins").tag("result", "success").register(registry);
        this.loginsFailure = Counter.builder("auth.logins").tag("result", "failure").register(registry);
        this.logouts = Counter.builder("auth.logouts").register(registry);
    }

    public void signup() {
        signups.increment();
    }

    public void loginSuccess() {
        loginsSuccess.increment();
    }

    public void loginFailure() {
        loginsFailure.increment();
    }

    public void logout() {
        logouts.increment();
    }
}
