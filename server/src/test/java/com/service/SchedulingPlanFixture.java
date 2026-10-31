package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogSchedulingDTO.*;
import com.dto.SchedulingPlanDTO.*;
import com.model.*;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.*;
import com.security.AccountPrincipal;
import com.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Transactional
abstract class SchedulingPlanFixture {
    @Autowired SchedulingPlanService plans;
    @Autowired CatalogService catalogs;
    @Autowired CatalogSchedulingService catalogScheduler;
    @Autowired AutoScheduleService automatic;
    @Autowired ScheduleService calendars;
    @Autowired TeacherRepository teachers;
    @Autowired SchedulingEntryRepository entries;
    @Autowired SchedulingPlanVersionRepository versions;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    String school;
    DepartmentView department, otherDepartment;
    LecturerView lecturer, otherLecturer;
    RoomView room;
    String adminId, userId;

    @BeforeEach void setup() {
        school = "PLAN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        adminId = school + "_ADMIN"; userId = school + "_USER"; authenticate(adminId, "ADMIN");
        teachers.saveAndFlush(Teacher.builder().teacherId(adminId).username(adminId).role("ADMIN").semesterStartDate(CatalogSchedulingTest.START).build());
        teachers.saveAndFlush(Teacher.builder().teacherId(userId).username(userId).role("TEACHER").semesterStartDate(CatalogSchedulingTest.START).build());
        catalogs.createInstitution(new InstitutionInput(school, "Trường kiểm thử", true, null));
        department = catalogs.createDepartment(school, new DepartmentInput("FIT", "Khoa CNTT", true, null));
        otherDepartment = catalogs.createDepartment(school, new DepartmentInput("BUS", "Khoa Kinh tế", true, null));
        teachers.saveAndFlush(Teacher.builder().teacherId(school + "_L1").username(school + "_L1").role("TEACHER").semesterStartDate(CatalogSchedulingTest.START).build());
        teachers.saveAndFlush(Teacher.builder().teacherId(school + "_L2").username(school + "_L2").role("TEACHER").semesterStartDate(CatalogSchedulingTest.START).build());
        lecturer = catalogs.createLecturer(school, new LecturerInput("GV01", "Nguyễn Văn A", department.id(), LecturerType.STAFF, school + "_L1", true, null));
        otherLecturer = catalogs.createLecturer(school, new LecturerInput("GV02", "Trần Thị B", otherDepartment.id(), LecturerType.STAFF, school + "_L2", true, null));
        catalogs.createAssignment(school, lecturer.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        catalogs.createAssignment(school, otherLecturer.id(), new AssignmentInput("NET", "Networking", "2024", 1, 0, true, null));
        room = catalogs.createRoom(school, new RoomInput("101", "Phòng chung", "THEORY", 60, null, true, null));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    static void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AccountPrincipal(id, id, role), null, List.of()));
    }
    Detail create(DepartmentView dept) throws Exception {
        return plans.create(new Create(school, dept.id(), "Lịch " + dept.departmentCode(), "HK1-2026", CatalogSchedulingTest.START));
    }
    Detail generate(Detail plan, LecturerView who, int hours) throws Exception {
        var options = plans.options(plan.plan().id());
        return plans.generate(plan.plan().id(), plan.plan().revision(), CatalogSchedulingTest.plan(hours, 0), config(options, who.id(), room.id()));
    }
    static Configuration config(Options options, Long lecturerId, Long roomId) {
        return new Configuration(options.snapshot(), List.of(lecturerId), List.of(roomId), List.of(), options.periodChoices().getFirst().id(), false);
    }
    List<TeachingCourse> projection(Long id) {
        return teachers.findAllWithFullSchedule().stream().flatMap(t -> t.getSchools().stream()).flatMap(s -> s.getCourses().stream()).filter(c -> id.equals(c.getSchedulingPlanId())).toList();
    }

}
