package com.service;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ScheduleAdjustmentTest extends ScheduleAdjustmentContract {}
