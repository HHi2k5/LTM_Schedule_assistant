package com.service;

import com.dto.*;
import com.model.Teacher;
import com.repository.TeacherRepository;
import com.security.AccountPrincipal;
import com.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:schedule-realtime;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.flyway.enabled=false", "app.jwt.secret="
})
@ActiveProfiles("test")
@DirtiesContext
class ScheduleRealtimeIntegrationTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    @LocalServerPort int port;
    @Autowired TeacherRepository teachers;
    @Autowired ScheduleService schedules;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;

    @Test
    void aRealAuthenticatedStreamReportsCommittedSavesAndDeletesAndTheWeekApiReturnsTheNewData() throws Exception {
        teachers.saveAndFlush(Teacher.builder().teacherId("LIVE_GV").fullName("Live teacher").username("live-teacher")
                .role("TEACHER").semesterStartDate(START).build());
        var admin = new AccountPrincipal("LIVE_ADMIN", "admin", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var reads = Executors.newVirtualThreadPerTaskExecutor();
        String token = tokens.issue("LIVE_GV");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var response = client.send(request("/api/schedule/events", token).header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());
            assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElseThrow());
            try (var input = response.body(); var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                awaitEvent("ready", reader, input, reads);
                schedules.saveOrUpdateSchoolSchedule(upload("101"));
                awaitEvent("schedule-changed", reader, input, reads);
                assertEquals("101", week(client, token).path("lessons").get(0).path("room").asText());

                schedules.saveOrUpdateSchoolSchedule(upload("202"));
                awaitEvent("schedule-changed", reader, input, reads);
                assertEquals("202", week(client, token).path("lessons").get(0).path("room").asText());

                schedules.deleteSchoolSchedule("LIVE_GV", "LIVE");
                awaitEvent("schedule-changed", reader, input, reads);
                assertEquals(0, week(client, token).path("lessons").size());
            }
        } finally {
            reads.shutdownNow();
            SecurityContextHolder.clearContext();
        }
    }

    private HttpRequest.Builder request(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token);
    }

    private com.fasterxml.jackson.databind.JsonNode week(HttpClient client, String token) throws Exception {
        var response = client.send(request("/api/schedule/week/1?teacherId=LIVE_GV", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private void awaitEvent(String wanted, BufferedReader reader, InputStream input, ExecutorService executor) throws Exception {
        var next = executor.submit(() -> {
            String type = "", line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) type = line.substring(6).trim();
                if (line.isEmpty()) {
                    if (wanted.equals(type)) return true;
                    type = "";
                }
            }
            return false;
        });
        try { assertTrue(next.get(15, TimeUnit.SECONDS), "Stream ended before " + wanted); }
        catch (Exception error) { input.close(); next.cancel(true); throw error; }
    }

    private SchoolUploadRequest upload(String room) {
        return SchoolUploadRequest.builder().teacherId("LIVE_GV").teacherName("Live teacher").semesterStartDate(START)
                .school(SchoolConfigDTO.builder().schoolId("LIVE").schoolName("Live school")
                        .periods(List.of(new PeriodTimeDTO(1, LocalTime.of(7, 0), LocalTime.of(7, 50)))).build())
                .courses(List.of(TeachingCourseDTO.builder().courseCode("NET").courseName("Live networking")
                        .room(room).dayOfWeek(2).startPeriod(1).numPeriods(1).weekSchedule("1").build())).build();
    }
}
