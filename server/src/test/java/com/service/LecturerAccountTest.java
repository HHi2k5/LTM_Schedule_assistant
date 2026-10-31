package com.service;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lecturer-account-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000") @AutoConfigureMockMvc @ActiveProfiles("test")
class LecturerAccountTest extends LecturerAccountContract {}
