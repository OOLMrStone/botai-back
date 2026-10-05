package org.botai.back.learning;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class UpgradeIntegrationTest {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
    @Test void preservesV4UsersHashesVerificationAndSession() throws Exception {
        var baseline=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("4").load();baseline.migrate();
        UUID local=UUID.randomUUID(),external=UUID.randomUUID();String session=UUID.randomUUID().toString();List<Integer> checksums=new ArrayList<>();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())) {
            try(var q=c.createStatement().executeQuery("SELECT checksum FROM flyway_schema_history ORDER BY installed_rank")) { while(q.next())checksums.add(q.getInt(1)); }
            try(var p=c.prepareStatement("INSERT INTO users(id,email,password_hash,email_verified,created_at) VALUES(?,'local@example.test','{bcrypt}unchanged',false,'2024-01-01T00:00:00Z')")) { p.setObject(1,local);p.executeUpdate(); }
            try(var p=c.prepareStatement("INSERT INTO users(id,email,auth_provider,provider_id,email_verified) VALUES(?,'oauth@example.test','GOOGLE','subject-123',true)")) { p.setObject(1,external);p.executeUpdate(); }
            try(var p=c.prepareStatement("INSERT INTO spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) VALUES(?,?,1,1,1209600,9999999999999,'local@example.test')")) { p.setString(1,session);p.setString(2,session);p.executeUpdate(); }
        }
        var latest=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load();assertThat(latest.migrate().migrationsExecuted).isEqualTo(3);
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())) {
            try(var q=c.createStatement().executeQuery("SELECT checksum FROM flyway_schema_history WHERE version IN('1','2','3','4') ORDER BY installed_rank")) { var after=new ArrayList<Integer>();while(q.next())after.add(q.getInt(1));assertThat(after).isEqualTo(checksums); }
            try(var q=c.createStatement().executeQuery("SELECT password_hash,email_verified,created_at,plan_id,time_zone FROM users WHERE email='local@example.test'")) { assertThat(q.next()).isTrue();assertThat(q.getString(1)).isEqualTo("{bcrypt}unchanged");assertThat(q.getBoolean(2)).isFalse();assertThat(q.getTimestamp(3).toInstant().toString()).isEqualTo("2024-01-01T00:00:00Z");assertThat(q.getString(4)).isEqualTo("free");assertThat(q.getString(5)).isEqualTo("Europe/Moscow"); }
            try(var q=c.createStatement().executeQuery("SELECT password_hash,email_verified,provider_id FROM users WHERE email='oauth@example.test'")) { assertThat(q.next()).isTrue();assertThat(q.getString(1)).isNull();assertThat(q.getBoolean(2)).isTrue();assertThat(q.getString(3)).isEqualTo("subject-123"); }
            try(var q=c.createStatement().executeQuery("SELECT count(*) FROM spring_session")) { q.next();assertThat(q.getInt(1)).isEqualTo(1); }
        }
    }
}
