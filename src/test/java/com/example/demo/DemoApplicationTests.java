package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
