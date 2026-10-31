package com.service;

import com.dto.CatalogDTO.*;
import com.exception.ResourceNotFoundException;
import com.model.*;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
    private final InstitutionRepository institutions;
    private final DepartmentRepository departments;
    private final InstitutionLecturerRepository lecturers;
    private final LecturerAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final TeacherRepository accounts;
    private final ScheduleWriteLock writeLock;
    private final LecturerBusySlotRepository busySlots;
    private final LecturerAccountService lecturerAccounts;

    public List<InstitutionView> listInstitutions() {
        return institutions.findAllByOrderBySchoolCodeAsc().stream().map(this::view).toList();
    }

    @Transactional
    public InstitutionView createInstitution(InstitutionInput input) {
        required(input);
        String code = schoolCode(input.schoolCode());
        writeLock.acquire();
        if (institutions.existsById(code)) throw new DataIntegrityViolationException("Mã trường đã tồn tại.");
        var entity = new Institution();
        entity.setSchoolCode(code);
        entity.setName(text(input.name(), 255, "Tên trường"));
        entity.setActive(active(input.active(), true));
        return view(institutions.saveAndFlush(entity));
    }

    @Transactional
    public InstitutionView updateInstitution(String schoolCode, InstitutionInput input) {
        required(input); writeLock.acquire();
        var entity = institution(schoolCode);
        revision(input.revision(), entity.getRevision());
        immutable(entity.getSchoolCode(), schoolCode(input.schoolCode()));
        entity.setName(text(input.name(), 255, "Tên trường"));
        entity.setActive(active(input.active(), false));
        return view(institutions.saveAndFlush(entity));
    }

    public List<DepartmentView> listDepartments(String schoolCode) {
        var school = institution(schoolCode);
        return departments.findByInstitution_SchoolCodeOrderByDepartmentCodeAsc(school.getSchoolCode()).stream().map(this::view).toList();
    }

    @Transactional
    public DepartmentView createDepartment(String schoolCode, DepartmentInput input) {
        required(input); writeLock.acquire();
        var entity = new Department();
        entity.setInstitution(activeInstitution(schoolCode));
        entity.setDepartmentCode(code(input.departmentCode(), "Mã khoa"));
        setDepartment(entity, input, true);
        return view(departments.saveAndFlush(entity));
    }

    @Transactional
    public DepartmentView updateDepartment(String schoolCode, Long id, DepartmentInput input) {
        required(input); writeLock.acquire();
        var entity = department(schoolCode, id);
        revision(input.revision(), entity.getRevision());
        immutable(entity.getDepartmentCode(), code(input.departmentCode(), "Mã khoa"));
        setDepartment(entity, input, false);
        return view(departments.saveAndFlush(entity));
    }

    public List<LecturerView> listLecturers(String schoolCode) {
        var school = institution(schoolCode);
        return lecturers.findByInstitution_SchoolCodeOrderByLecturerCodeAsc(school.getSchoolCode()).stream().map(this::view).toList();
    }

    @Transactional
    public LecturerView createLecturer(String schoolCode, LecturerInput input) {
        required(input); writeLock.acquire();
        var entity = new InstitutionLecturer();
        entity.setInstitution(activeInstitution(schoolCode));
        entity.setLecturerCode(code(input.lecturerCode(), "Mã giảng viên"));
        if (lecturers.existsByInstitution_SchoolCodeAndLecturerCode(entity.getInstitution().getSchoolCode(), entity.getLecturerCode()))
            throw new DataIntegrityViolationException("Mã giảng viên đã tồn tại trong trường.");
        setLecturer(entity, input, true);
        return view(lecturers.saveAndFlush(entity));
    }

    @Transactional
    public LecturerView updateLecturer(String schoolCode, Long id, LecturerInput input) {
        required(input); writeLock.acquire();
        var entity = lecturer(schoolCode, id);
        revision(input.revision(), entity.getRevision());
        immutable(entity.getLecturerCode(), code(input.lecturerCode(), "Mã giảng viên"));
        setLecturer(entity, input, false);
        return view(lecturers.saveAndFlush(entity));
    }

    @Transactional
    public AccountBatchResult createMissingAccounts(String schoolCode, AccountBatch input) {
        SchedulingPlanService.admin(); required(input); writeLock.acquire(); activeInstitution(schoolCode);
        if (input.lecturers() == null || input.lecturers().isEmpty() || input.lecturers().size() > 10000)
            throw new IllegalArgumentException("Chọn 1–10000 giảng viên cần tạo tài khoản.");
        var seen = new java.util.HashSet<Long>(); var result = new java.util.ArrayList<LecturerView>(); int created = 0;
        for (var target : input.lecturers()) {
            if (target == null || target.id() == null || !seen.add(target.id())) throw new IllegalArgumentException("Danh sách GV bị trống hoặc lặp.");
            var entity = lecturer(schoolCode, target.id()); revision(target.revision(), entity.getRevision());
            if (!entity.isActive()) throw new IllegalArgumentException("Chỉ tạo tài khoản cho giảng viên đang hoạt động.");
            if (entity.getAccount() == null) { provision(entity, true); lecturers.saveAndFlush(entity); created++; }
            result.add(view(entity));
        }
        return new AccountBatchResult(created, result);
    }

    public List<AssignmentView> listAssignments(String schoolCode, Long lecturerId) {
        var lecturer = lecturer(schoolCode, lecturerId);
        return assignments.findByLecturer_IdOrderByCourseCodeAscCohortAsc(lecturer.getId()).stream().map(this::view).toList();
    }

    List<AssignmentView> listSchoolAssignments(String schoolCode) {
        var school = institution(schoolCode);
        return assignments.findByLecturer_Institution_SchoolCodeOrderByIdAsc(school.getSchoolCode()).stream().map(this::view).toList();
    }

    @Transactional
    public AssignmentView createAssignment(String schoolCode, Long lecturerId, AssignmentInput input) {
        required(input); writeLock.acquire();
        var entity = new LecturerAssignment();
        entity.setLecturer(lecturer(schoolCode, lecturerId));
        entity.setCourseCode(code(input.courseCode(), "Mã môn"));
        entity.setCohort(text(input.cohort(), 50, "Khóa sinh viên").toUpperCase(Locale.ROOT));
        setAssignment(entity, input, true);
        return view(assignments.saveAndFlush(entity));
    }

    @Transactional
    public AssignmentView updateAssignment(String schoolCode, Long lecturerId, Long id, AssignmentInput input) {
        required(input); writeLock.acquire();
        lecturer(schoolCode, lecturerId);
        var entity = assignments.findByIdAndLecturer_Id(id, lecturerId).orElseThrow(() -> missing("Phân công"));
        revision(input.revision(), entity.getRevision());
        immutable(entity.getCourseCode(), code(input.courseCode(), "Mã môn"));
        immutable(entity.getCohort(), text(input.cohort(), 50, "Khóa sinh viên").toUpperCase(Locale.ROOT));
        setAssignment(entity, input, false);
        return view(assignments.saveAndFlush(entity));
    }

    public List<RoomView> listRooms(String schoolCode) {
        var school = institution(schoolCode);
        return rooms.findBySchoolCodeOrderByCapacityAsc(school.getSchoolCode()).stream().map(this::view).toList();
    }

    @Transactional
    public RoomView createRoom(String schoolCode, RoomInput input) {
        required(input); writeLock.acquire();
        var school = activeInstitution(schoolCode);
        var entity = new Room();
        entity.setSchoolCode(school.getSchoolCode());
        entity.setRoomCode(code(input.roomCode(), "Mã phòng"));
        setRoom(entity, input, true);
        return view(rooms.saveAndFlush(entity));
    }

    @Transactional
    public RoomView updateRoom(String schoolCode, Long id, RoomInput input) {
        required(input); writeLock.acquire();
        var school = institution(schoolCode);
        var entity = rooms.findByIdAndSchoolCode(id, school.getSchoolCode()).orElseThrow(() -> missing("Phòng học"));
        revision(input.revision(), entity.getRevision());
        immutable(entity.getRoomCode(), code(input.roomCode(), "Mã phòng"));
        setRoom(entity, input, false);
        return view(rooms.saveAndFlush(entity));
    }

    private void setDepartment(Department entity, DepartmentInput input, boolean creating) {
        entity.setName(text(input.name(), 255, "Tên khoa"));
        entity.setActive(active(input.active(), creating));
    }

    private void setLecturer(InstitutionLecturer entity, LecturerInput input, boolean creating) {
        entity.setFullName(text(input.fullName(), 100, "Họ tên"));
        if (input.lecturerType() == null) throw new IllegalArgumentException("Cần chọn giảng viên cơ hữu hoặc thỉnh giảng.");
        entity.setLecturerType(input.lecturerType());
        entity.setDepartment(input.departmentId() == null ? null : department(entity.getInstitution().getSchoolCode(), input.departmentId()));
        Teacher nextAccount = entity.getAccount();
        String declaredIdentity = input.existingScheduleTeacherId() != null && !input.existingScheduleTeacherId().isBlank() ? input.existingScheduleTeacherId().strip()
                : entity.getScheduleTeacher() == null ? null : entity.getScheduleTeacher().getTeacherId();
        if (nextAccount == null && declaredIdentity != null) {
            var old = accounts.findById(declaredIdentity).orElse(null);
            if (old != null && old.isAutoProvisioned() && entity.getLecturerCode().equals(old.getUsername())) nextAccount = old;
        }
        if (input.accountTeacherId() != null && !input.accountTeacherId().isBlank()) {
            var account = accounts.findById(code(input.accountTeacherId(), "Mã tài khoản GV"))
                    .orElseThrow(() -> missing("Tài khoản giảng viên"));
            if (!"TEACHER".equals(account.getRole()) || account.getUsername() == null || account.getUsername().isBlank())
                throw new IllegalArgumentException("Chỉ liên kết tài khoản giảng viên đã đăng ký.");
            nextAccount = account;
        }
        var mapping = validateScheduleIdentity(entity.getInstitution().getSchoolCode(),
                entity.getScheduleTeacher() == null ? null : entity.getScheduleTeacher().getTeacherId(),
                entity.getAccount() == null ? null : entity.getAccount().getTeacherId(),
                nextAccount == null ? null : nextAccount.getTeacherId(), input.existingScheduleTeacherId());
        entity.setAccount(nextAccount);
        entity.setScheduleTeacher(mapping);
        entity.setActive(active(input.active(), creating));
        if (entity.isActive() && entity.getInstitution().isActive() && entity.getAccount() == null) provision(entity, !creating);
    }

    private void provision(InstitutionLecturer entity, boolean retainMapping) {
        var identity = entity.getScheduleTeacher();
        var account = lecturerAccounts.ensure(entity.getLecturerCode(), entity.getFullName(), identity, identity == null ? null : identity.getSemesterStartDate());
        entity.setAccount(account);
        if (identity != null || retainMapping) entity.setScheduleTeacher(account);
    }

    Teacher validateScheduleIdentity(String school, String previousId, String previousAccount, String nextAccount, String declaredId) {
        Teacher previous = previousId == null ? null : accounts.findTeacherWithFullSchedule(previousId).orElseThrow(() -> missing("Hồ sơ lịch GV"));
        boolean hasSchedule = previous != null && (previous.getSchools().stream().anyMatch(s -> !s.getCourses().isEmpty()) || busySlots.countActiveForTeacher(previousId) > 0);
        if (!java.util.Objects.equals(previousAccount, nextAccount)) {
            if (hasSchedule && !previousId.equals(nextAccount)) throw new IllegalArgumentException("Giảng viên đã có lịch. Cần đối chiếu lịch trước khi đổi hoặc bỏ liên kết tài khoản.");
            if (!hasSchedule) previous = null;
        }
        if (declaredId == null || declaredId.isBlank()) return previous;
        String id = text(declaredId, 50, "Mã hồ sơ lịch cũ");
        if (hasSchedule && !previousId.equals(id)) throw new IllegalArgumentException("Không đổi hồ sơ lịch khi đã có buổi dạy; cần đối chiếu lịch trước.");
        var candidate = accounts.findTeacherWithFullSchedule(id).orElseThrow(() -> missing("Hồ sơ lịch cũ"));
        if (!"TEACHER".equals(candidate.getRole())) throw new IllegalArgumentException("Hồ sơ lịch cũ không phải hồ sơ giảng viên.");
        if (nextAccount != null && !id.equals(nextAccount)) throw new IllegalArgumentException("Hồ sơ lịch cũ khác tài khoản đã liên kết; cần đối chiếu hai lịch.");
        if (candidate.getUsername() != null && !candidate.getUsername().isBlank() && !id.equals(nextAccount))
            throw new IllegalArgumentException("Hồ sơ này có tài khoản đăng ký. Hãy liên kết đúng mã tài khoản GV.");
        if (candidate.getSchools().stream().noneMatch(s -> s.getSchoolCode().equalsIgnoreCase(school)) && !id.equals(nextAccount))
            throw new IllegalArgumentException("Hồ sơ lịch cũ chưa có dữ liệu thuộc trường đang chọn.");
        return candidate;
    }

    private void setAssignment(LecturerAssignment entity, AssignmentInput input, boolean creating) {
        entity.setCourseName(text(input.courseName(), 180, "Tên môn"));
        entity.setTheoryWeight(weight(input.theoryWeight()));
        entity.setPracticeWeight(weight(input.practiceWeight()));
        entity.setActive(active(input.active(), creating));
        if (entity.isActive() && entity.getTheoryWeight() == 0 && entity.getPracticeWeight() == 0)
            throw new IllegalArgumentException("Phân công đang hoạt động cần ít nhất một trọng số LT/TH lớn hơn 0.");
    }

    private void setRoom(Room entity, RoomInput input, boolean creating) {
        entity.setRoomName(text(input.roomName(), 100, "Tên phòng"));
        if (input.capacity() == null || input.capacity() < 1 || input.capacity() > 10000)
            throw new IllegalArgumentException("Sức chứa phải từ 1 đến 10000.");
        entity.setCapacity(input.capacity());
        String type = text(input.roomType(), 30, "Loại phòng").toUpperCase(Locale.ROOT);
        switch (type) {
            case "PRACTICE", "LAB_PRACTICE" -> { entity.setLab(true); entity.setRoomType("LAB_PRACTICE"); }
            case "THEORY", "THEORY_LARGE", "THEORY_MEDIUM", "THEORY_STANDARD" -> {
                entity.setLab(false);
                entity.setRoomType(input.capacity() >= 120 ? "THEORY_LARGE" : input.capacity() >= 80 ? "THEORY_MEDIUM" : "THEORY_STANDARD");
            }
            default -> throw new IllegalArgumentException("Loại phòng phải là THEORY hoặc PRACTICE.");
        }
        entity.setBuilding(optionalText(input.building(), 50, "Tòa nhà"));
        entity.setActive(active(input.active(), creating));
    }

    private Institution institution(String schoolCode) {
        return institutions.findById(schoolCode(schoolCode)).orElseThrow(() -> missing("Trường"));
    }

    private Institution activeInstitution(String schoolCode) {
        var school = institution(schoolCode);
        if (!school.isActive()) throw new IllegalArgumentException("Trường đã ngừng hoạt động.");
        return school;
    }

    private Department department(String schoolCode, Long id) {
        return departments.findByIdAndInstitution_SchoolCode(id, schoolCode(schoolCode))
                .orElseThrow(() -> missing("Khoa trong trường này"));
    }

    private InstitutionLecturer lecturer(String schoolCode, Long id) {
        return lecturers.findByIdAndInstitution_SchoolCode(id, schoolCode(schoolCode))
                .orElseThrow(() -> missing("Giảng viên trong trường này"));
    }

    static String text(String value, int limit, String label) {
        String result = value == null ? null : value.strip();
        ScheduleValidation.text(result, limit, label);
        if (result.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(label + " chứa ký tự điều khiển.");
        return result;
    }

    private static String optionalText(String value, int limit, String label) {
        return value == null || value.isBlank() ? null : text(value, limit, label);
    }

    static String code(String value, String label) {
        String result = text(value, 50, label).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9][A-Z0-9._/-]{0,49}"))
            throw new IllegalArgumentException(label + " chỉ gồm chữ A–Z, số, dấu chấm, gạch dưới, gạch ngang hoặc gạch chéo.");
        return result;
    }

    private static boolean active(Boolean value, boolean creating) {
        if (value == null && !creating) throw new IllegalArgumentException("Cần cung cấp trạng thái active khi cập nhật.");
        return value == null || value;
    }

    static String schoolCode(String value) {
        String result = code(value, "Mã trường");
        if (result.contains("/")) throw new IllegalArgumentException("Mã trường không chứa dấu gạch chéo.");
        return result;
    }

    static int weight(Integer value) {
        if (value == null || value < 0 || value > 10000) throw new IllegalArgumentException("Trọng số LT/TH phải từ 0 đến 10000.");
        return value;
    }

    private static void required(Object input) {
        if (input == null) throw new IllegalArgumentException("Thiếu dữ liệu danh mục.");
    }

    private static void revision(Long expected, long actual) {
        if (expected == null) throw new IllegalArgumentException("Cần cung cấp revision khi cập nhật.");
        if (expected != actual) throw new ObjectOptimisticLockingFailureException("Catalog", expected);
    }

    private static void immutable(String previous, String next) {
        if (!previous.equalsIgnoreCase(next)) throw new IllegalArgumentException("Không đổi mã định danh; hãy ngừng sử dụng bản ghi cũ và tạo bản ghi mới.");
    }

    private static ResourceNotFoundException missing(String label) { return new ResourceNotFoundException(label + " không tồn tại."); }

    private InstitutionView view(Institution e) { return new InstitutionView(e.getSchoolCode(), e.getName(), e.isActive(), e.getRevision()); }
    private DepartmentView view(Department e) { return new DepartmentView(e.getId(), e.getInstitution().getSchoolCode(), e.getDepartmentCode(), e.getName(), e.isActive(), e.getRevision()); }
    private LecturerView view(InstitutionLecturer e) { return new LecturerView(e.getId(), e.getInstitution().getSchoolCode(), e.getLecturerCode(), e.getFullName(), e.getDepartment() == null ? null : e.getDepartment().getId(), e.getLecturerType(), e.getAccount() == null ? null : e.getAccount().getTeacherId(), e.isActive(), e.getRevision(), e.getScheduleTeacher() == null ? null : e.getScheduleTeacher().getTeacherId(), e.getAccount() == null ? null : e.getAccount().getUsername(), e.getAccount() != null && e.getAccount().isAutoProvisioned()); }
    private AssignmentView view(LecturerAssignment e) { return new AssignmentView(e.getId(), e.getLecturer().getId(), e.getCourseCode(), e.getCourseName(), e.getCohort(), e.getTheoryWeight(), e.getPracticeWeight(), e.isActive(), e.getRevision()); }
    private RoomView view(Room e) { return new RoomView(e.getId(), e.getSchoolCode(), e.getRoomCode(), e.getRoomName(), e.getRoomType(), e.getCapacity(), e.isLab(), e.getBuilding(), e.isActive(), e.getRevision()); }
}
