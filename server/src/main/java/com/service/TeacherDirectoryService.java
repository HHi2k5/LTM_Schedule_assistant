package com.service;

import com.dto.TeacherAccountDTO;
import com.model.Department;
import com.model.Subject;
import com.model.Teacher;
import com.model.TeacherSubject;
import com.repository.DepartmentRepository;
import com.repository.SubjectRepository;
import com.repository.TeacherRepository;
import com.repository.TeacherSubjectRepository;
import com.security.ScheduleAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/** Hồ sơ giảng viên của khoa: bộ môn, loại giảng viên, môn phụ trách và môn có thể dạy thay. */
@Service
@RequiredArgsConstructor
public class TeacherDirectoryService {
    private final TeacherRepository teachers;
    private final TeacherSubjectRepository teacherSubjects;
    private final DepartmentRepository departments;
    private final SubjectRepository subjects;
    private final PasswordEncoder passwordEncoder;
    private final ScheduleAccess access;

    /** Mật khẩu mặc định: tên không dấu viết liền (chữ thường) + mã GV. VD: "Đào Ngọc Phong" + "GV01" -> "daongocphongGV01". */
    public static String defaultPassword(String fullName, String teacherId) {
        String noAccent = Normalizer.normalize(fullName == null ? "" : fullName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return noAccent + teacherId.trim().toUpperCase(Locale.ROOT);
    }

    private static String kind(String value) {
        return TeacherSubject.KIND_SUBSTITUTE.equalsIgnoreCase(value) ? TeacherSubject.KIND_SUBSTITUTE : TeacherSubject.KIND_PRIMARY;
    }

    private static TeacherAccountDTO.SubjectAssignment view(TeacherSubject row) {
        return TeacherAccountDTO.SubjectAssignment.builder().id(row.getId()).courseCode(row.getCourseCode())
                .courseName(row.getCourseName()).cohort(row.getCohort()).theoryWeight(row.getTheoryWeight())
                .practiceWeight(row.getPracticeWeight()).kind(kind(row.getAssignmentKind())).build();
    }

    /** Admin xem mọi giảng viên (lọc theo bộ môn nếu cần); trưởng bộ môn chỉ xem bộ môn mình và không thấy mật khẩu mặc định. */
    @Transactional(readOnly = true)
    public List<TeacherAccountDTO> list(String schoolId, Long departmentId) {
        String schoolCode = CatalogService.school(schoolId);
        boolean admin = access.isAdmin();
        if (!admin) {
            Long own = access.headDepartmentId();
            if (own == null) throw new AccessDeniedException("Bạn không có quyền xem danh sách giảng viên.");
            departmentId = own;
        }
        var departmentNames = new HashMap<Long, String>();
        var schoolDepartments = new HashSet<Long>();
        for (Department department : departments.findBySchoolCodeOrderByCodeAsc(schoolCode)) {
            departmentNames.put(department.getId(), department.getName());
            schoolDepartments.add(department.getId());
        }
        var accounts = new TreeMap<String, Teacher>();
        for (Teacher teacher : teachers.findAll()) {
            if (!teacher.unassignedPool()) accounts.put(teacher.getTeacherId().toUpperCase(Locale.ROOT), teacher);
        }

        var grouped = new LinkedHashMap<String, TeacherAccountDTO>();
        for (TeacherSubject row : teacherSubjects.findBySchoolCodeOrderByTeacherIdAscCourseCodeAsc(schoolCode)) {
            String id = row.getTeacherId().trim().toUpperCase(Locale.ROOT);
            Teacher account = accounts.get(id);
            if (departmentId != null && (account == null || !departmentId.equals(account.getDepartmentId()))) continue;
            grouped.computeIfAbsent(id, key -> profile(schoolCode, id, row.getTeacherName(), account, departmentNames, admin))
                    .getSubjects().add(view(row));
        }
        // Giảng viên đã thuộc bộ môn nhưng chưa có môn nào vẫn cần hiện để bổ sung môn.
        for (var entry : accounts.entrySet()) {
            Teacher account = entry.getValue();
            if (account.getDepartmentId() == null || !schoolDepartments.contains(account.getDepartmentId())) continue;
            if (departmentId != null && !departmentId.equals(account.getDepartmentId())) continue;
            grouped.computeIfAbsent(entry.getKey(), key -> profile(schoolCode, key, account.getFullName(), account, departmentNames, admin));
        }
        return new ArrayList<>(grouped.values());
    }

    private TeacherAccountDTO profile(String schoolCode, String id, String name, Teacher account,
                                      Map<Long, String> departmentNames, boolean admin) {
        String username = account != null && account.getUsername() != null && !account.getUsername().isBlank()
                ? account.getUsername() : id;
        return TeacherAccountDTO.builder().schoolCode(schoolCode).teacherId(id).teacherName(name)
                .username(admin ? username : null)
                .defaultPassword(admin ? defaultPassword(name, id) : null)
                .departmentId(account == null ? null : account.getDepartmentId())
                .departmentName(account == null ? null : departmentNames.get(account.getDepartmentId()))
                .teacherType(account == null ? Teacher.TYPE_LECTURER : account.effectiveType())
                .role(account == null ? null : account.getRole())
                .subjects(new ArrayList<>()).build();
    }

    @Transactional
    public TeacherAccountDTO save(TeacherAccountDTO payload) {
        if (payload == null) throw new IllegalArgumentException("Thiếu thông tin giảng viên.");
        boolean admin = access.isAdmin();
        Long ownDept = admin ? null : access.headDepartmentId();
        if (!admin && ownDept == null) {
            throw new AccessDeniedException("Bạn không có quyền quản lý giảng viên.");
        }

        String schoolCode = CatalogService.school(payload.getSchoolCode());
        if (payload.getTeacherId() == null || payload.getTeacherId().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Mã giảng viên.");
        }
        if (payload.getTeacherName() == null || payload.getTeacherName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập Họ tên giảng viên.");
        }
        String teacherId = payload.getTeacherId().trim().toUpperCase(Locale.ROOT);
        String teacherName = payload.getTeacherName().trim();
        ScheduleValidation.text(teacherId, 50, "Mã giảng viên");
        ScheduleValidation.text(teacherName, 100, "Họ tên giảng viên");
        if (Teacher.UNASSIGNED_ID.equals(teacherId)) throw new IllegalArgumentException("Mã giảng viên này được hệ thống sử dụng.");

        Long targetDeptId = admin ? payload.getDepartmentId() : ownDept;
        if (targetDeptId == null) throw new IllegalArgumentException("Vui lòng chọn bộ môn của giảng viên.");
        if (!admin && payload.getDepartmentId() != null && !ownDept.equals(payload.getDepartmentId())) {
            throw new AccessDeniedException("Trưởng bộ môn chỉ được thao tác trong bộ môn của mình.");
        }

        Department department = departments.findById(targetDeptId)
                .filter(found -> found.getSchoolCode().equals(schoolCode))
                .orElseThrow(() -> new IllegalArgumentException("Bộ môn không còn tồn tại."));

        // Gom danh sách môn học (hỗ trợ cả mảng subjects nhiều môn lẫn trường courseCode đơn lẻ)
        var subjectList = new ArrayList<TeacherAccountDTO.SubjectAssignment>();
        if (payload.getSubjects() != null && !payload.getSubjects().isEmpty()) {
            subjectList.addAll(payload.getSubjects());
        } else if (payload.getCourseCode() != null && !payload.getCourseCode().isBlank()) {
            subjectList.add(TeacherAccountDTO.SubjectAssignment.builder().id(payload.getSubjectAssignmentId())
                    .courseCode(payload.getCourseCode()).courseName(payload.getCourseName()).cohort(payload.getCohort())
                    .theoryWeight(payload.getTheoryWeight()).practiceWeight(payload.getPracticeWeight()).build());
        }
        subjectList.removeIf(subject -> subject == null || subject.getCourseCode() == null || subject.getCourseCode().isBlank());
        if (subjectList.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ít nhất một môn học cho giảng viên.");

        var catalog = new HashMap<String, Subject>();
        for (Subject subject : subjects.findByDepartmentIdOrderByCourseCodeAsc(department.getId())) {
            catalog.put(subject.getCourseCode().toUpperCase(Locale.ROOT), subject);
        }
        for (var subject : subjectList) {
            String code = subject.getCourseCode().trim().toUpperCase(Locale.ROOT);
            if (!catalog.containsKey(code)) {
                throw new IllegalArgumentException("Môn " + code + " không thuộc bộ môn " + department.getName()
                        + ". Hãy thêm môn ở trang Bộ môn & Môn học trước.");
            }
            for (Integer weight : new Integer[]{subject.getTheoryWeight(), subject.getPracticeWeight()}) {
                if (weight != null && (weight < 0 || weight > 10000)) {
                    throw new IllegalArgumentException("Trọng số LT/TH của môn " + code + " phải từ 0 đến 10000.");
                }
            }
        }

        Teacher teacher = teachers.findById(teacherId).orElse(null);
        String generatedPassword = defaultPassword(teacherName, teacherId);
        Long previousDepartment = teacher == null ? null : teacher.getDepartmentId();

        String requestedType = payload.getTeacherType() == null || payload.getTeacherType().isBlank()
                ? (teacher == null ? Teacher.TYPE_LECTURER : teacher.effectiveType())
                : payload.getTeacherType().trim().toUpperCase(Locale.ROOT);
        if (!Teacher.TYPES.contains(requestedType)) throw new IllegalArgumentException("Loại giảng viên không hợp lệ.");

        String effectiveType;
        if (!admin) {
            if (teacher == null) {
                if (!Teacher.TYPE_VISITING.equals(requestedType)) {
                    throw new AccessDeniedException("Trưởng bộ môn chỉ được thêm mới hồ sơ Giảng viên Thỉnh giảng.");
                }
                teacher = Teacher.builder()
                        .teacherId(teacherId)
                        .fullName(teacherName)
                        .username(teacherId)
                        .password(passwordEncoder.encode(generatedPassword))
                        .role(Teacher.ROLE_TEACHER)
                        .teacherType(Teacher.TYPE_VISITING)
                        .departmentId(ownDept)
                        .semesterStartDate(LocalDate.of(2026, 8, 10))
                        .build();
                teachers.save(teacher);
            } else {
                if (Teacher.ROLE_ADMIN.equals(teacher.getRole()) || teacher.unassignedPool()) {
                    throw new AccessDeniedException("Không thể chỉnh sửa tài khoản quản trị/hệ thống.");
                }
                if (teacher.getDepartmentId() == null || !ownDept.equals(teacher.getDepartmentId())) {
                    throw new AccessDeniedException("Giảng viên " + teacherId + " không thuộc bộ môn của bạn.");
                }
                // Trưởng bộ môn chỉ cập nhật môn dạy, giữ nguyên định danh, vai trò và loại giảng viên hiện có.
                teacherName = teacher.getFullName();
            }
            effectiveType = teacher.effectiveType();
        } else {
            if (teacher == null) {
                teacher = Teacher.builder().teacherId(teacherId).fullName(teacherName).username(teacherId)
                        .password(passwordEncoder.encode(generatedPassword)).role(Teacher.ROLE_TEACHER)
                        .semesterStartDate(LocalDate.of(2026, 8, 10)).build();
            } else {
                teacher.setFullName(teacherName);
                if (teacher.getUsername() == null || teacher.getUsername().isBlank()) teacher.setUsername(teacherId);
                if (teacher.getPassword() == null || teacher.getPassword().isBlank()) {
                    teacher.setPassword(passwordEncoder.encode(generatedPassword));
                }
            }
            effectiveType = requestedType;
            teacher.setDepartmentId(department.getId());
            teacher.setTeacherType(effectiveType);
            // Vai trò đăng nhập đi theo loại giảng viên; tài khoản quản trị không bị đổi vai trò.
            if (!Teacher.ROLE_ADMIN.equals(teacher.getRole())) {
                teacher.setRole(Teacher.TYPE_HEAD.equals(effectiveType) ? Teacher.ROLE_DEPT_HEAD : Teacher.ROLE_TEACHER);
            }
            teachers.save(teacher);

            if (Teacher.TYPE_HEAD.equals(effectiveType)) {
                // Mỗi bộ môn chỉ có một trưởng bộ môn: người cũ trở lại làm giảng viên.
                for (Teacher other : teachers.findByDepartmentId(department.getId())) {
                    if (other.getTeacherId().equals(teacherId)) continue;
                    if (!Teacher.TYPE_HEAD.equals(other.effectiveType()) && !Teacher.ROLE_DEPT_HEAD.equals(other.getRole())) continue;
                    other.setTeacherType(Teacher.TYPE_LECTURER);
                    if (!Teacher.ROLE_ADMIN.equals(other.getRole())) other.setRole(Teacher.ROLE_TEACHER);
                    teachers.save(other);
                }
            }
            if (previousDepartment != null && !previousDepartment.equals(department.getId())) {
                // Chuyển bộ môn: bỏ các môn của bộ môn cũ.
                for (TeacherSubject row : teacherSubjects.findBySchoolCodeAndTeacherIdIgnoreCase(schoolCode, teacherId)) {
                    if (!catalog.containsKey(row.getCourseCode().toUpperCase(Locale.ROOT))) teacherSubjects.delete(row);
                }
            }
        }

        var saved = new ArrayList<TeacherAccountDTO.SubjectAssignment>();
        for (var subject : subjectList) {
            String code = subject.getCourseCode().trim().toUpperCase(Locale.ROOT);
            String cohort = subject.getCohort() == null || subject.getCohort().isBlank() ? "*" : subject.getCohort().trim();
            TeacherSubject row = teacherSubjects
                    .findBySchoolCodeAndTeacherIdIgnoreCaseAndCourseCodeIgnoreCaseAndCohortIgnoreCase(schoolCode, teacherId, code, cohort)
                    .orElse(null);
            if (row == null && subject.getId() != null) {
                row = teacherSubjects.findById(subject.getId())
                        .filter(found -> found.getTeacherId().equalsIgnoreCase(teacherId)).orElse(null);
            }
            if (row == null) row = new TeacherSubject();
            row.setSchoolCode(schoolCode);
            row.setTeacherId(teacherId);
            row.setTeacherName(teacherName);
            row.setCourseCode(code);
            row.setCourseName(catalog.get(code).getCourseName());
            row.setCohort(cohort);
            row.setTheoryWeight(subject.getTheoryWeight() != null ? subject.getTheoryWeight() : 1);
            row.setPracticeWeight(subject.getPracticeWeight() != null ? subject.getPracticeWeight() : 1);
            row.setAssignmentKind(kind(subject.getKind()));
            saved.add(view(teacherSubjects.save(row)));
        }
        // Đồng bộ tên hiển thị trên các môn khác của giảng viên.
        for (TeacherSubject row : teacherSubjects.findBySchoolCodeAndTeacherIdIgnoreCase(schoolCode, teacherId)) {
            if (!teacherName.equals(row.getTeacherName())) {
                row.setTeacherName(teacherName);
                teacherSubjects.save(row);
            }
        }

        return TeacherAccountDTO.builder().schoolCode(schoolCode).teacherId(teacherId).teacherName(teacherName)
                .username(admin ? teacher.getUsername() : null)
                .defaultPassword(admin ? generatedPassword : null)
                .departmentId(department.getId()).departmentName(department.getName())
                .teacherType(effectiveType).role(teacher.getRole()).subjects(saved).build();
    }

    @Transactional
    public void deleteAssignment(Long id) {
        TeacherSubject row = teacherSubjects.findById(id).orElse(null);
        if (row == null) return;
        if (!access.isAdmin()) {
            Long ownDept = access.headDepartmentId();
            if (ownDept == null) throw new AccessDeniedException("Bạn không có quyền gỡ môn học của giảng viên.");
            Teacher target = teachers.findById(row.getTeacherId().trim().toUpperCase(Locale.ROOT)).orElse(null);
            if (target == null || target.getDepartmentId() == null || !ownDept.equals(target.getDepartmentId())) {
                throw new AccessDeniedException("Bạn chỉ được gỡ môn học của giảng viên thuộc bộ môn mình.");
            }
        }
        teacherSubjects.delete(row);
    }
}
