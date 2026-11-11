package com.dto;

import java.time.LocalDate;
import java.util.List;

/** Dữ liệu trang phân công giảng viên vào các lớp khoa đã xếp phòng. */
public final class AssignmentView {
    private AssignmentView() {}

    public record Session(int dayOfWeek, int startPeriod, int numPeriods, String room, String weekSchedule) {}

    /** Giảng viên dạy được môn của lớp; blockedReason khác null khi không thể nhận lớp (trùng giờ...). */
    public record Candidate(String teacherId, String teacherName, String teacherType, String kind, int weight,
                            String blockedReason) {}

    public record ClassRow(String classId, String courseCode, String courseName, String label, String sessionType,
                           Long departmentId, String departmentName, String teacherId, String teacherName,
                           int totalPeriods, List<Session> sessions, List<Candidate> candidates) {}

    public record TeacherLoad(String teacherId, String teacherName, String teacherType, int classes, int periods) {}

    public record Overview(String schoolId, Long departmentId, LocalDate semesterStartDate,
                           List<ClassRow> classes, List<TeacherLoad> teachers) {}

    /** teacherId rỗng hoặc null = bỏ phân công (đưa lớp về trạng thái chưa có giảng viên). */
    public record AssignRequest(String schoolId, String classId, String teacherId) {}

    public record AutoRequest(String schoolId, Long departmentId) {}

    public record Unassigned(String classId, String label, String reason) {}

    public record AutoResult(String status, String message, int assigned, List<Unassigned> remaining) {}
}
