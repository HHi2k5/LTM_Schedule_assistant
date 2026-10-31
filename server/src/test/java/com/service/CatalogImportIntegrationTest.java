package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogImportDTO.Kind;
import com.model.InstitutionLecturer.LecturerType;
import com.model.*;
import com.repository.*;
import com.security.TokenService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CatalogImportIntegrationTest {
    @Autowired CatalogImportService imports;
    @Autowired CatalogService catalogs;
    @Autowired LegacyCatalogService legacy;
    @Autowired TeacherRepository accounts;
    @Autowired RoomRepository rooms;
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;

    @BeforeEach void setup() {
        catalogs.createInstitution(new InstitutionInput("IMP_A", "Trường A", true, null));
        catalogs.createInstitution(new InstitutionInput("IMP_B", "Trường B", true, null));
    }

    @Test void previewDoesNotWriteAndCommitUpsertsOnlyTheReviewedFile() throws Exception {
        var old = catalogs.createRoom("IMP_A", new RoomInput("101", "Phòng cũ", "THEORY", 60, "A2", false, null));
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa", "Tòa nhà"}, new Object[][]{{"101", "THEORY", 80, ""}, {"LAB", "PRACTICE", 30, "A2"}});
        var preview = imports.preview("IMP_A", Kind.ROOMS, file);
        assertEquals(1, preview.created()); assertEquals(1, preview.updated()); assertTrue(preview.canCommit());
        assertEquals(60, catalogs.listRooms("IMP_A").getFirst().capacity());
        assertEquals(1, catalogs.listRooms("IMP_A").size());
        var result = imports.commit("IMP_A", Kind.ROOMS, file, preview.snapshot());
        assertEquals(1, result.created()); assertEquals(1, result.updated());
        var stored = catalogs.listRooms("IMP_A");
        assertEquals(2, stored.size()); assertTrue(stored.stream().anyMatch(r -> r.isLab() && r.capacity() == 30));
        var previous = stored.stream().filter(r -> r.id().equals(old.id())).findFirst().orElseThrow();
        assertFalse(previous.active()); assertEquals("A2", previous.building()); assertEquals("Phòng cũ", previous.roomName());
        var repeated = imports.preview("IMP_A", Kind.ROOMS, file);
        assertEquals(2, repeated.unchanged()); assertFalse(repeated.canCommit());
    }

    @Test void fileErrorsAndDuplicateCodesBlockTheWholeImport() throws Exception {
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 60}, {"101", "PRACTICE", 30}, {"BAD", "THEORY", -1}});
        var preview = imports.preview("IMP_A", Kind.ROOMS, file);
        assertEquals(1, preview.created()); assertEquals(2, preview.invalid()); assertFalse(preview.canCommit());
        assertEquals(3, preview.rows().get(1).rowNumber());
        assertThrows(IllegalArgumentException.class, () -> imports.commit("IMP_A", Kind.ROOMS, file, preview.snapshot()));
        assertTrue(catalogs.listRooms("IMP_A").isEmpty());
    }

    @Test void legacyRoomCodeCaseAndInactiveStatusArePreservedAcrossRepeatedImports() throws Exception {
        rooms.saveAndFlush(Room.builder().schoolCode("IMP_A").roomCode("lab-old").roomName("Phòng LAB cũ")
                .roomType("LAB_PRACTICE").capacity(30).isLab(true).active(false).build());
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"LAB-OLD", "PRACTICE", 30}});
        assertEquals(1, imports.preview("IMP_A", Kind.ROOMS, file).unchanged());
        var changed = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"LAB-OLD", "PRACTICE", 35}});
        var preview = imports.preview("IMP_A", Kind.ROOMS, changed);
        assertEquals(1, preview.updated()); imports.commit("IMP_A", Kind.ROOMS, changed, preview.snapshot());
        assertEquals("lab-old", catalogs.listRooms("IMP_A").getFirst().roomCode());
        assertFalse(catalogs.listRooms("IMP_A").getFirst().active());
        assertEquals(1, imports.preview("IMP_A", Kind.ROOMS, changed).unchanged());
    }

    @Test void rejectsChangedFileOrAnotherSchoolsSnapshot() throws Exception {
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 60}});
        var changed = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 61}});
        var preview = imports.preview("IMP_A", Kind.ROOMS, file);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> imports.commit("IMP_A", Kind.ROOMS, changed, preview.snapshot()));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> imports.commit("IMP_B", Kind.ROOMS, file, preview.snapshot()));
        assertTrue(catalogs.listRooms("IMP_A").isEmpty()); assertTrue(catalogs.listRooms("IMP_B").isEmpty());
    }

    @Test void catalogChangeBetweenPreviewAndCommitRequiresAnotherPreview() throws Exception {
        var old = catalogs.createRoom("IMP_A", new RoomInput("101", "Phòng", "THEORY", 60, null, true, null));
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 100}});
        var preview = imports.preview("IMP_A", Kind.ROOMS, file);
        catalogs.updateRoom("IMP_A", old.id(), new RoomInput("101", "Phòng vừa đổi", "THEORY", 70, null, true, old.revision()));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> imports.commit("IMP_A", Kind.ROOMS, file, preview.snapshot()));
        assertEquals(70, catalogs.listRooms("IMP_A").getFirst().capacity());
    }

    @Test void lecturerImportResolvesFacultyAndAccountWithoutCreatingCredentials() throws Exception {
        account("IMP_TEACHER", "TEACHER"); long count = accounts.count();
        var department = catalogs.createDepartment("IMP_A", new DepartmentInput("FIT", "CNTT", true, null));
        var file = file(new String[]{"Mã GV", "Họ tên", "Mã khoa", "Loại GV", "Mã tài khoản", "Trạng thái"},
                new Object[][]{{"L01", "Nguyễn Văn A", "FIT", "Thỉnh giảng", "IMP_TEACHER", "TRUE"}});
        var preview = imports.preview("IMP_A", Kind.LECTURERS, file);
        assertEquals(0, preview.invalid());
        imports.commit("IMP_A", Kind.LECTURERS, file, preview.snapshot());
        var lecturer = catalogs.listLecturers("IMP_A").getFirst();
        assertEquals(department.id(), lecturer.departmentId()); assertEquals("IMP_TEACHER", lecturer.accountTeacherId());
        assertEquals(LecturerType.VISITING, lecturer.lecturerType()); assertEquals(count, accounts.count());
        var minimal = file(new String[]{"Mã GV", "Họ tên"}, new Object[][]{{"L01", "Tên mới"}});
        var update = imports.preview("IMP_A", Kind.LECTURERS, minimal);
        imports.commit("IMP_A", Kind.LECTURERS, minimal, update.snapshot());
        lecturer = catalogs.listLecturers("IMP_A").getFirst();
        assertEquals(department.id(), lecturer.departmentId()); assertEquals("IMP_TEACHER", lecturer.accountTeacherId());
        assertEquals(LecturerType.VISITING, lecturer.lecturerType());
    }

    @Test void foreignFacultyAndRepeatedAccountAreReportedBeforeWriting() throws Exception {
        account("IMP_TEACHER", "TEACHER");
        catalogs.createDepartment("IMP_B", new DepartmentInput("FOREIGN", "Khoa B", true, null));
        var file = file(new String[]{"Mã GV", "Họ tên", "Mã khoa", "Mã tài khoản"}, new Object[][]{
                {"L01", "Một", "", "IMP_TEACHER"}, {"L02", "Hai", "", "IMP_TEACHER"}, {"L03", "Ba", "FOREIGN", ""}});
        var preview = imports.preview("IMP_A", Kind.LECTURERS, file);
        assertEquals(2, preview.invalid()); assertFalse(preview.canCommit()); assertTrue(catalogs.listLecturers("IMP_A").isEmpty());
    }

    @Test void assignmentsRequireExistingLecturersAndKeepLtThWeights() throws Exception {
        var lecturer = catalogs.createLecturer("IMP_A", new LecturerInput("L01", "GV", null, LecturerType.STAFF, null, true, null));
        var file = file(new String[]{"Mã GV", "Mã môn", "Tên học phần", "Khóa", "Trọng số LT", "Trọng số TH"},
                new Object[][]{{"L01", "NET", "Mạng máy tính", "2024", 2, 8}});
        var preview = imports.preview("IMP_A", Kind.ASSIGNMENTS, file);
        assertTrue(preview.canCommit()); imports.commit("IMP_A", Kind.ASSIGNMENTS, file, preview.snapshot());
        var stored = catalogs.listAssignments("IMP_A", lecturer.id()).getFirst();
        assertEquals(2, stored.theoryWeight()); assertEquals(8, stored.practiceWeight());
        assertEquals(1, imports.preview("IMP_A", Kind.ASSIGNMENTS, file).unchanged());
        assertEquals(1, imports.preview("IMP_B", Kind.ASSIGNMENTS, file).invalid());
    }

    @Test void formulasAndCorruptFilesAreRejected() throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(); var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã phòng"); header.createCell(1).setCellValue("Loại phòng"); header.createCell(2).setCellValue("Sức chứa");
            var row = sheet.createRow(1); row.createCell(0).setCellValue("101"); row.createCell(1).setCellValue("THEORY"); row.createCell(2).setCellFormula("30+30");
            book.write(output);
            var preview = imports.preview("IMP_A", Kind.ROOMS, new MockMultipartFile("file", "rooms.xlsx", "application/octet-stream", output.toByteArray()));
            assertEquals(1, preview.invalid()); assertFalse(preview.canCommit());
        }
        assertThrows(IllegalArgumentException.class, () -> imports.preview("IMP_A", Kind.ROOMS, new MockMultipartFile("file", "bad.xlsx", "application/octet-stream", new byte[]{1,2,3})));
    }

    @Test void httpEndpointsEnforceRolesAndReturnPreviewAndCommitCounts() throws Exception {
        account("IMP_ADMIN", "ADMIN"); account("IMP_TEACHER", "TEACHER");
        var file = file(new String[]{"Mã phòng", "Loại phòng", "Sức chứa"}, new Object[][]{{"101", "THEORY", 60}});
        String path = "/api/admin/institutions/IMP_A/imports/rooms";
        mvc.perform(multipart(path + "/preview").file(file)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path + "/preview").file(file).header("Authorization", "Bearer " + tokens.issue("IMP_TEACHER"))).andExpect(status().isForbidden());
        var preview = imports.preview("IMP_A", Kind.ROOMS, file);
        mvc.perform(multipart(path + "/commit").file(file).param("snapshot", preview.snapshot()).header("Authorization", "Bearer " + tokens.issue("IMP_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
    }

    @Test void legacySuggestionsShowConflictingNamesAndOnlyExplicitAccountLinks() {
        var teacher = account("IMP_TEACHER", "TEACHER");
        teacher.getSchools().add(School.builder().teacher(teacher).schoolCode("LEGACY").schoolName("Tên A").build());
        accounts.saveAndFlush(teacher);
        var other = account("IMP_OTHER", "TEACHER");
        other.getSchools().add(School.builder().teacher(other).schoolCode("LEGACY").schoolName("Tên B").build()); accounts.saveAndFlush(other);
        rooms.saveAndFlush(Room.builder().schoolCode("LEGACY").roomCode("101").roomType("THEORY_STANDARD").capacity(60).build());
        var candidate = legacy.preview().stream().filter(s -> s.schoolCode().equals("LEGACY")).findFirst().orElseThrow();
        assertEquals(List.of("Tên A", "Tên B"), candidate.names()); assertEquals(1, candidate.roomCount()); assertFalse(candidate.registered());
        assertEquals(2, candidate.lecturers().size()); assertTrue(candidate.lecturers().stream().allMatch(l -> l.accountTeacherId() != null));
        assertTrue(catalogs.listInstitutions().stream().noneMatch(s -> s.schoolCode().equals("LEGACY")));
    }

    private Teacher account(String id, String role) {
        return accounts.saveAndFlush(Teacher.builder().teacherId(id).username(id).fullName("Teacher").role(role)
                .password("keep-this-hash").semesterStartDate(LocalDate.of(2026, 8, 10)).build());
    }
    static MockMultipartFile file(String[] headers, Object[][] rows) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("DanhMuc"); var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    var cell = row.createCell(j);
                    if (rows[i][j] instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(String.valueOf(rows[i][j]));
                }
            }
            book.write(output);
            return new MockMultipartFile("file", "catalog.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, output.toByteArray());
        }
    }
}
