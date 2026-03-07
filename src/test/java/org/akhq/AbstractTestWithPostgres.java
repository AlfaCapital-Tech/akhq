package org.akhq;

import io.micronaut.core.annotation.NonNull;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

abstract public class AbstractTestWithPostgres extends AbstractTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("akhq")
                .withUsername("akhq")
                .withPassword("akhq");
        POSTGRES.start();
    }

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("datasources.default.url", POSTGRES.getJdbcUrl());
        props.put("datasources.default.username", POSTGRES.getUsername());
        props.put("datasources.default.password", POSTGRES.getPassword());
        props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        props.put("flyway.datasources.default.enabled", "true");
        props.put("jpa.default.entity-scan.packages", "org.akhq.models.accessmanagement");
        props.put("jpa.default.properties.hibernate.hbm2ddl.auto", "validate");
        return props;
    }
}
