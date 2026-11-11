package com.service;

import com.dto.DepartmentDTO;
import com.model.*;
import com.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/** Danh mục của khoa: bộ môn, môn học, loại phòng và phòng học. */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final DepartmentRepository departments;
    private final SubjectRepository subjects;
    private final RoomTypeRepository roomTypes;
    private final RoomRepository rooms;
    private final TeacherRepository teachers;
    private final TeacherSubjectRepository teacherSubjects;

    private static final List<RoomType> DEFAULT_ROOM_TYPES = List.of(
            RoomType.builder().code("LY_THUYET").name("Phòng lý thuyết").usageKind(RoomType.THEORY).build(),
            RoomType.builder().code("MAY_CHIEU").name("Phòng máy chiếu").usageKind(RoomType.THEORY).build(),
            RoomType.builder().code("PHONG_MAY").name("Phòng máy").usageKind(RoomType.PRACTICE).build(),
            RoomType.builder().code("THUC_HANH").name("Phòng thực hành").usageKind(RoomType.PRACTICE).build());

    public static String school(String code) {
        return code == null || code.isBlank() ? "PTIT" : code.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "Phòng máy chiếu" -> "PHONG_MAY_CHIEU". */
    static String slug(String value, int max) {
        String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return ascii.length() > max ? ascii.substring(0, max) : ascii;
    }

    // ==================== LOẠI PHÒNG ====================

    /** Tạo loại phòng mặc định cho đơn vị chưa có và chuyển phòng mang loại cũ sang mã loại phòng mới. */
    @Transactional
    public void ensureRoomTypes(String schoolCode) {
        String code = school(schoolCode);
        if (roomTypes.countBySchoolCode(code) == 0) {
            for (var type : DEFAULT_ROOM_TYPES) {
                roomTypes.save(RoomType.builder().schoolCode(code).code(type.getCode()).name(type.getName())
                        .usageKind(type.getUsageKind()).build());
            }
        }
        var known = roomTypes.findBySchoolCodeOrderByUsageKindDescNameAsc(code);
        var codes = new HashSet<String>();
        known.forEach(type -> codes.add(type.getCode().toUpperCase(Locale.ROOT)));
        for (Room room : rooms.findBySchoolCodeOrderByCapacityAsc(code)) {
            if (room.getRoomType() != null && codes.contains(room.getRoomType().toUpperCase(Locale.ROOT))) continue;
            String preferred = room.isLab() ? "THUC_HANH" : "LY_THUYET";
            String fallback = codes.contains(preferred) ? preferred : known.stream()
                    .filter(type -> type.practice() == room.isLab()).map(RoomType::getCode).findFirst().orElse(null);
            if (fallback == null) continue;
            room.setRoomType(fallback);
            rooms.save(room);
        }
    }

    @Transactional
    public List<RoomType> listRoomTypes(String schoolCode) {
        ensureRoomTypes(schoolCode);
        return roomTypes.findBySchoolCodeOrderByUsageKindDescNameAsc(school(schoolCode));
    }

    @Transactional
    public RoomType saveRoomType(RoomType payload) {
        if (payload == null) throw new IllegalArgumentException("Thiếu thông tin loại phòng.");
        String schoolCode = school(payload.getSchoolCode());
        ensureRoomTypes(schoolCode);
        ScheduleValidation.text(payload.getName(), 100, "Tên loại phòng");
        if (!RoomType.THEORY.equals(payload.getUsageKind()) && !RoomType.PRACTICE.equals(payload.getUsageKind())) {
            throw new IllegalArgumentException("Chọn loại phòng dùng cho lớp lý thuyết hay thực hành.");
        }
        RoomType type;
        if (payload.getId() != null) {
            type = roomTypes.findById(payload.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Loại phòng không còn tồn tại."));
        } else {
            String code = slug(blankToNull(payload.getCode()) != null ? payload.getCode() : payload.getName(), 30);
            if (code.isEmpty()) throw new IllegalArgumentException("Tên loại phòng cần có chữ hoặc số.");
            if (roomTypes.findBySchoolCodeAndCodeIgnoreCase(schoolCode, code).isPresent()) {
                throw new IllegalArgumentException("Loại phòng " + code + " đã tồn tại.");
            }
            type = RoomType.builder().schoolCode(schoolCode).code(code).build();
        }
        boolean usageChanged = type.getUsageKind() != null && !type.getUsageKind().equals(payload.getUsageKind());
        type.setName(payload.getName().trim());
        type.setUsageKind(payload.getUsageKind());
        RoomType saved = roomTypes.save(type);
        if (usageChanged) {
            // Môn đang yêu cầu loại phòng này cho nhóm lớp cũ thì không còn hợp lệ.
            for (Subject subject : subjects.findBySchoolCodeOrderByCourseCodeAsc(schoolCode)) {
                if (saved.getCode().equalsIgnoreCase(subject.getTheoryRoomType())
                        || saved.getCode().equalsIgnoreCase(subject.getPracticeRoomType())) {
                    throw new IllegalArgumentException("Môn " + subject.getCourseCode()
                            + " đang yêu cầu loại phòng này; bỏ yêu cầu ở môn học trước khi đổi nhóm LT/TH.");
                }
            }
            for (Room room : rooms.findBySchoolCodeAndRoomTypeIgnoreCase(schoolCode, saved.getCode())) {
                room.setLab(saved.practice());
                rooms.save(room);
            }
        }
        return saved;
    }

    @Transactional
    public void deleteRoomType(Long id) {
        RoomType type = roomTypes.findById(id).orElse(null);
        if (type == null) return;
        if (!rooms.findBySchoolCodeAndRoomTypeIgnoreCase(type.getSchoolCode(), type.getCode()).isEmpty()) {
            throw new IllegalArgumentException("Còn phòng học thuộc loại \"" + type.getName() + "\"; đổi loại các phòng đó trước khi xóa.");
        }
        for (Subject subject : subjects.findBySchoolCodeOrderByCourseCodeAsc(type.getSchoolCode())) {
            if (type.getCode().equalsIgnoreCase(subject.getTheoryRoomType())
                    || type.getCode().equalsIgnoreCase(subject.getPracticeRoomType())) {
                throw new IllegalArgumentException("Môn " + subject.getCourseCode() + " đang yêu cầu loại phòng \""
                        + type.getName() + "\"; bỏ yêu cầu ở môn học trước khi xóa.");
            }
        }
        roomTypes.delete(type);
    }

    // ==================== PHÒNG HỌC ====================

    @Transactional
    public List<Room> listRooms(String schoolCode) {
        ensureRoomTypes(schoolCode);
        return rooms.findBySchoolCodeOrderByCapacityAsc(school(schoolCode));
    }

    @Transactional
    public Room saveRoom(Room payload) {
        if (payload == null) throw new IllegalArgumentException("Thiếu thông tin phòng học.");
        String schoolCode = school(payload.getSchoolCode());
        ensureRoomTypes(schoolCode);
        ScheduleValidation.text(payload.getRoomCode(), 50, "Mã phòng");
        String roomCode = payload.getRoomCode().trim();

        String typeCode = blankToNull(payload.getRoomType());
        // Giá trị cũ của giao diện trước đây: THEORY / PRACTICE.
        if (typeCode == null) typeCode = payload.isLab() ? "THUC_HANH" : "LY_THUYET";
        else if (typeCode.equalsIgnoreCase("THEORY")) typeCode = "LY_THUYET";
        else if (typeCode.equalsIgnoreCase("PRACTICE")) typeCode = "THUC_HANH";
        RoomType type = roomTypes.findBySchoolCodeAndCodeIgnoreCase(schoolCode, typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Loại phòng không tồn tại. Hãy chọn lại loại phòng."));

        int capacity = payload.getCapacity() > 0 ? payload.getCapacity() : (type.practice() ? 30 : 60);
        if (capacity > 1000) throw new IllegalArgumentException("Sức chứa phòng tối đa 1000.");
        String note = blankToNull(payload.getNote());
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");

        Room room = payload.getId() != null
                ? rooms.findById(payload.getId()).orElse(new Room())
                : rooms.findBySchoolCodeAndRoomCodeIgnoreCase(schoolCode, roomCode).orElse(new Room());
        var sameCode = rooms.findBySchoolCodeAndRoomCodeIgnoreCase(schoolCode, roomCode);
        if (sameCode.isPresent() && room.getId() != null && !sameCode.get().getId().equals(room.getId())) {
            throw new IllegalArgumentException("Mã phòng " + roomCode + " đã tồn tại.");
        }

        String roomName = type.getName() + " " + roomCode;
        room.setRoomCode(roomCode);
        room.setRoomName(roomName.length() > 100 ? roomName.substring(0, 100) : roomName);
        room.setRoomType(type.getCode());
        room.setCapacity(capacity);
        room.setLab(type.practice());
        room.setBuilding(blankToNull(payload.getBuilding()) == null ? "A2" : payload.getBuilding().trim());
        room.setSchoolCode(schoolCode);
        room.setNote(note);
        return rooms.save(room);
    }

    @Transactional
    public void deleteRoom(Long id) {
        rooms.deleteById(id);
    }

    // ==================== BỘ MÔN ====================

    @Transactional(readOnly = true)
    public List<DepartmentDTO> listDepartments(String schoolCode) {
        var result = new ArrayList<DepartmentDTO>();
        for (Department department : departments.findBySchoolCodeOrderByCodeAsc(school(schoolCode))) {
            var members = teachers.findByDepartmentId(department.getId());
            Teacher head = members.stream().filter(t -> Teacher.ROLE_DEPT_HEAD.equals(t.getRole())).findFirst().orElse(null);
            result.add(new DepartmentDTO(department.getId(), department.getSchoolCode(), department.getCode(), department.getName(),
                    head == null ? null : head.getTeacherId(), head == null ? null : head.getFullName(),
                    subjects.countByDepartmentId(department.getId()), members.size()));
        }
        return result;
    }

    @Transactional
    public Department saveDepartment(Department payload) {
        if (payload == null) throw new IllegalArgumentException("Thiếu thông tin bộ môn.");
        String schoolCode = school(payload.getSchoolCode());
        ScheduleValidation.text(payload.getName(), 100, "Tên bộ môn");
        String code = payload.getCode() == null ? "" : payload.getCode().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) code = initials(payload.getName());
        if (!code.matches("[A-Z0-9_]{1,20}")) {
            throw new IllegalArgumentException("Mã bộ môn gồm 1–20 chữ in hoa, số hoặc dấu gạch dưới (VD: CNPM).");
        }
        Department department = payload.getId() == null ? Department.builder().schoolCode(schoolCode).build()
                : departments.findById(payload.getId()).orElseThrow(() -> new IllegalArgumentException("Bộ môn không còn tồn tại."));
        var sameCode = departments.findBySchoolCodeAndCodeIgnoreCase(department.getSchoolCode(), code);
        if (sameCode.isPresent() && !sameCode.get().getId().equals(department.getId())) {
            throw new IllegalArgumentException("Mã bộ môn " + code + " đã tồn tại.");
        }
        department.setCode(code);
        department.setName(payload.getName().trim());
        return departments.save(department);
    }

    /** "Công nghệ phần mềm" -> "CNPM". */
    private static String initials(String name) {
        var builder = new StringBuilder();
        for (String word : slug(name, 200).split("_")) if (!word.isEmpty()) builder.append(word.charAt(0));
        return builder.length() > 20 ? builder.substring(0, 20) : builder.toString();
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = departments.findById(id).orElse(null);
        if (department == null) return;
        if (subjects.countByDepartmentId(id) > 0) {
            throw new IllegalArgumentException("Bộ môn còn môn học; xóa hoặc chuyển các môn trước.");
        }
        if (teachers.countByDepartmentId(id) > 0) {
            throw new IllegalArgumentException("Bộ môn còn giảng viên; chuyển giảng viên sang bộ môn khác trước.");
        }
        departments.delete(department);
    }

    // ==================== MÔN HỌC ====================

    @Transactional(readOnly = true)
    public List<Subject> listSubjects(String schoolCode, Long departmentId) {
        return departmentId != null ? subjects.findByDepartmentIdOrderByCourseCodeAsc(departmentId)
                : subjects.findBySchoolCodeOrderByCourseCodeAsc(school(schoolCode));
    }

    /** Gợi ý mã môn kế tiếp: mã bộ môn + số thứ tự 3 chữ số, bỏ qua các mã đã dùng. */
    @Transactional(readOnly = true)
    public String nextSubjectCode(Long departmentId) {
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Bộ môn không còn tồn tại."));
        var used = new HashSet<String>();
        subjects.findBySchoolCodeOrderByCourseCodeAsc(department.getSchoolCode())
                .forEach(subject -> used.add(subject.getCourseCode().toUpperCase(Locale.ROOT)));
        int index = (int) subjects.countByDepartmentId(departmentId) + 1;
        String candidate;
        do { candidate = department.getCode() + String.format("%03d", index++); } while (used.contains(candidate));
        return candidate;
    }

    @Transactional
    public Subject saveSubject(Subject payload) {
        if (payload == null || payload.getDepartmentId() == null) throw new IllegalArgumentException("Chọn bộ môn cho môn học.");
        Department department = departments.findById(payload.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Bộ môn không còn tồn tại."));
        String schoolCode = department.getSchoolCode();
        ensureRoomTypes(schoolCode);
        ScheduleValidation.text(payload.getCourseName(), 180, "Tên môn học");
        String name = payload.getCourseName().trim();
        String code = blankToNull(payload.getCourseCode()) == null ? nextSubjectCode(department.getId())
                : payload.getCourseCode().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("\\S{1,50}")) throw new IllegalArgumentException("Mã môn gồm 1–50 ký tự, không chứa khoảng trắng.");

        Subject subject = payload.getId() == null ? Subject.builder().schoolCode(schoolCode).build()
                : subjects.findById(payload.getId()).orElseThrow(() -> new IllegalArgumentException("Môn học không còn tồn tại."));
        var sameCode = subjects.findBySchoolCodeAndCourseCodeIgnoreCase(schoolCode, code);
        if (sameCode.isPresent() && !sameCode.get().getId().equals(subject.getId())) {
            String owner = departments.findById(sameCode.get().getDepartmentId()).map(Department::getName).orElse("khác");
            throw new IllegalArgumentException("Mã môn " + code + " đã tồn tại (bộ môn " + owner + ").");
        }

        String oldCode = subject.getCourseCode();
        if (oldCode != null) {
            var linked = teacherSubjects.findBySchoolCodeAndCourseCodeIgnoreCase(schoolCode, oldCode);
            if (!linked.isEmpty() && !department.getId().equals(subject.getDepartmentId())) {
                throw new IllegalArgumentException("Môn đang được gắn cho " + linked.size()
                        + " giảng viên; gỡ môn khỏi các giảng viên trước khi chuyển bộ môn.");
            }
            // Giữ liên kết môn dạy của giảng viên khi đổi mã hoặc tên môn.
            for (TeacherSubject row : linked) {
                row.setCourseCode(code);
                row.setCourseName(name);
                teacherSubjects.save(row);
            }
        }

        subject.setDepartmentId(department.getId());
        subject.setCourseCode(code);
        subject.setCourseName(name);
        subject.setTheoryRoomType(requiredRoomType(schoolCode, payload.getTheoryRoomType(), false));
        subject.setPracticeRoomType(requiredRoomType(schoolCode, payload.getPracticeRoomType(), true));
        return subjects.save(subject);
    }

    private String requiredRoomType(String schoolCode, String value, boolean practice) {
        String code = blankToNull(value);
        if (code == null) return null;
        RoomType type = roomTypes.findBySchoolCodeAndCodeIgnoreCase(schoolCode, code)
                .orElseThrow(() -> new IllegalArgumentException("Loại phòng " + code + " không tồn tại."));
        if (type.practice() != practice) {
            throw new IllegalArgumentException("Loại phòng \"" + type.getName() + "\" không dùng cho lớp "
                    + (practice ? "thực hành." : "lý thuyết."));
        }
        return type.getCode();
    }

    @Transactional
    public void deleteSubject(Long id) {
        Subject subject = subjects.findById(id).orElse(null);
        if (subject == null) return;
        int linked = teacherSubjects.findBySchoolCodeAndCourseCodeIgnoreCase(subject.getSchoolCode(), subject.getCourseCode()).size();
        if (linked > 0) {
            throw new IllegalArgumentException("Môn đang được gắn cho " + linked + " giảng viên; gỡ môn khỏi các giảng viên trước khi xóa.");
        }
        subjects.delete(subject);
    }
}
