import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);


    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        log.info("flyway dataSource:"+ dataSource);
        return Flyway.configure()
                     .dataSource(dataSource)
                     .locations("classpath:db/migration")
                     .baselineOnMigrate(true)
                     .table("flyway_schema_history")
                     .load();
    }
}
