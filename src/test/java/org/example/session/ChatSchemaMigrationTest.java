package org.example.session;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Flyway 会话与长期记忆迁移可在真实 MySQL 8 实例执行。 */
@Testcontainers(disabledWithoutDocker = true)
class ChatSchemaMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("opsmind").withUsername("opsmind").withPassword("opsmind");

    @Test
    void migrationRenamesSequenceColumnWithoutLosingData() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO chat_session (
                            id, tenant_id, user_id, compacted_through_sequence, next_sequence,
                            memory_extracted_through_sequence, version, created_at, updated_at, expires_at
                        ) VALUES (
                            'session', 'tenant', 'user', 0, 42, 0, 0,
                            '2026-01-01 00:00:00.000', '2026-01-01 00:00:00.000', '2026-01-02 00:00:00.000'
                        )
                        """);
            }

            org.flywaydb.core.Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            try (var newColumn = connection.getMetaData().getColumns(null, null, "chat_session", "last_assigned_sequence")) {
                assertTrue(newColumn.next());
            }
            try (var oldColumn = connection.getMetaData().getColumns(null, null, "chat_session", "next_sequence")) {
                assertFalse(oldColumn.next());
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT last_assigned_sequence FROM chat_session WHERE id = 'session'")) {
                assertTrue(result.next());
                assertEquals(42, result.getLong(1));
            }
        }
    }
}
