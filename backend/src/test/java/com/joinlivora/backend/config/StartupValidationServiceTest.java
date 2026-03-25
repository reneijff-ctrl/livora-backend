package com.joinlivora.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartupValidationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Environment environment;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws Exception {
    }

    private void mockJdbc() throws Exception {
        lenient().when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.isValid(anyInt())).thenReturn(true);
        lenient().when(connection.getMetaData()).thenReturn(metaData);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
    }

    @Test
    void run_ValidConfigAndTables_ShouldPass() throws Exception {
        mockJdbc();
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, "key", "secret", "jwt");
        
        // Mock all tables existing
        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        // Mock high-risk users query
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(5);

        service.run();
    }

    @Test
    void run_MissingConfig_ShouldFail() {
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, null, "key", "secret", "jwt");

        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_InvalidConfig_ShouldFail() {
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 150, "key", "secret", "jwt");

        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_MissingTable_ShouldFail() throws Exception {
        mockJdbc();
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, "key", "secret", "jwt");

        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(resultSet);
        // Table missing (and its uppercase version)
        when(resultSet.next()).thenReturn(false);

        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_HighRiskQueryFails_ShouldFail() throws Exception {
        mockJdbc();
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, "key", "secret", "jwt");

        // Mock all tables existing
        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        // Mock high-risk users query failing
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenThrow(new RuntimeException("DB Error"));

        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_MissingStripeKey_ShouldFail() throws Exception {
        mockJdbc();
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, null, "secret", "jwt");
        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_MissingJwtSecret_ShouldFail() throws Exception {
        mockJdbc();
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, "key", "secret", null);
        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }

    @Test
    void run_MissingWebhookSecretInProd_ShouldFail() throws Exception {
        mockJdbc();
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        StartupValidationService service = new StartupValidationService(jdbcTemplate, environment, 30, "key", null, "jwt");
        assertThrows(StartupValidationService.StartupValidationException.class, () -> service.run());
    }
}








