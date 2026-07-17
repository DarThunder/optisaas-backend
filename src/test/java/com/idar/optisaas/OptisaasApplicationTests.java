package com.idar.optisaas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// El perfil 'test' aporta los secretos que producción exige por entorno (ver
// application-test.properties). Todo @SpringBootTest nuevo lo necesita, o no levantará
// el contexto. La BD la dan las variables DB_* (Postgres de servicio en CI).
@SpringBootTest
@ActiveProfiles("test")
class OptisaasApplicationTests {

	@Test
	void contextLoads() {
	}

}
