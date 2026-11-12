package com.chat

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["spring.datasource.url=jdbc:sqlite::memory:"])
@ActiveProfiles("test")
class ChatApplicationTests {

	@Test
	fun contextLoads() {
	}

}
