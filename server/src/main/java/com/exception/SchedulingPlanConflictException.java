package com.exception;

import com.dto.SchedulingPlanDTO.Conflict;
import java.util.List;

public class SchedulingPlanConflictException extends RuntimeException {
    private final List<Conflict> conflicts;
    public SchedulingPlanConflictException(List<Conflict> conflicts) {
        super("Bản nháp chưa thể xác nhận. Kiểm tra thiếu tiết, thay đổi dữ liệu và xung đột lịch.");
        this.conflicts = List.copyOf(conflicts);
    }
    public List<Conflict> getConflicts() { return conflicts; }
}
