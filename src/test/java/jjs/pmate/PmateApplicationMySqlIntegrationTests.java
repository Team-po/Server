package jjs.pmate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("ci")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PmateApplicationMySqlIntegrationTests {

	@Test
	void contextLoadsWithMySqlAndFlyway() {
	}

}
