package jjs.pmate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")
class PmateApplicationH2Tests {

	@Test
	void contextLoadsWithH2Profile() {
	}

}
