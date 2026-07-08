package com.tum.course.metrics;

import com.tum.course.repository.CourseRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Exposes the current number of courses in the database as a Prometheus gauge.
 *
 * Registered as "database.courses.total", but Micrometer strips the "_total"
 * suffix from gauges (it is reserved for counters in the OpenMetrics format),
 * so Prometheus/Grafana see it as "database_courses".
 *
 * The gauge calls repo.count() lazily on every Prometheus scrape (15s
 * interval), so the value is always current — no scheduled refresh needed.
 */
@Component
public class DatabaseMetrics {

    public DatabaseMetrics(CourseRepository courseRepository, MeterRegistry meterRegistry) {
        // gauge(name, stateObject, valueFunction): Micrometer keeps only a weak
        // reference to the state object; the Spring context holds the strong one.
        meterRegistry.gauge(
            "database.courses.total",
            courseRepository,
            repo -> (double) repo.count()
        );
    }
}
