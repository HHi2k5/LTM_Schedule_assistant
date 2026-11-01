package com.service;

import com.dto.PeriodTimeDTO;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;


public final class SchedulingPolicy {

    public static final int FORBIDDEN_MULTI_PERIOD_START = 5;
    public static final int NEXT_START = 7;
    public static final int MAX_THEORY_CLASS_PERIODS_PER_DAY = 3;
    public static final int MIN_PRACTICE_SESSION_PERIODS = 3;
    public static final int MAX_PRACTICE_SESSION_PERIODS = 4;
    public static final int PREFERRED_GAP_DAYS = 14;
    public static final int MIN_GAP_DAYS = 7;

    private static final LocalTime NOON_START = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);

    private SchedulingPolicy() {}

    /**
     * 3.1. Chuẩn hóa tiết bắt đầu:
     * - Nếu bắt đầu ở tiết 5 và dài > 1 tiết -> chuyển sang tiết 7 giữ nguyên số tiết.
     * - Nếu bắt đầu ở tiết 5 và dài == 1 tiết -> giữ nguyên cho LT (TH không có buổi 1 tiết).
     */
    public static int normalizeStartPeriod(int candidateStart, int periodCount) {
        if (candidateStart == FORBIDDEN_MULTI_PERIOD_START && periodCount > 1) {
            return NEXT_START;
        }
        return candidateStart;
    }

    /**
     * Trả về danh sách các tiết bắt đầu cần thử đã được chuẩn hóa và loại bỏ trùng lặp.
     */
    public static List<Integer> candidateStartPeriods(Collection<Integer> rawStarts, int periodCount) {
        var result = new LinkedHashSet<Integer>();
        for (int raw : rawStarts) {
            int normalized = normalizeStartPeriod(raw, periodCount);
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }

    /**
     * Kiểm tra một khung tiết có tồn tại đầy đủ và liên tục (khoảng nghỉ giữa 2 tiết liên tiếp <= 20 phút) hay không.
     */
    public static boolean isContiguousValidSlot(Map<Integer, PeriodTimeDTO> periods, int start, int count) {
        if (start == FORBIDDEN_MULTI_PERIOD_START && count > 1) {
            return false;
        }
        if (!ScheduleValidation.hasPeriods(periods, start, count)) {
            return false;
        }
        for (int p = start + 1; p < start + count; p++) {
            long gapMinutes = Duration.between(periods.get(p - 1).getEnd(), periods.get(p).getStart()).toMinutes();
            if (gapMinutes > 20) {
                return false;
            }
        }
        return true;
    }

    /**
     * 3.2. Kiểm tra tổng tiết TH có thể chia thành các buổi 3 hoặc 4 tiết hay không.
     * Các tổng 1, 2, 5 không thể biểu diễn bằng 3*n3 + 4*n4.
     */
    public static boolean canPartitionPracticePeriods(int totalPracticePeriods) {
        if (totalPracticePeriods == 0) return true;
        if (totalPracticePeriods < MIN_PRACTICE_SESSION_PERIODS) return false;
        for (int n4 = totalPracticePeriods / 4; n4 >= 0; n4--) {
            int rem = totalPracticePeriods - 4 * n4;
            if (rem % 3 == 0) return true;
        }
        return false;
    }

    /**
     * Sinh danh sách các phương án chia buổi TH (mỗi buổi 3 hoặc 4 tiết),
     * ưu tiên phương án ít buổi nhất trước (nhiều buổi 4 tiết trước, buổi 3 tiết sau),
     * kèm các phương án dự phòng (ví dụ 12 tiết: [4,4,4] rồi đến [3,3,3,3]).
     */
    public static List<List<Integer>> practicePartitions(int totalPracticePeriods) {
        if (totalPracticePeriods <= 0) return List.of(List.of());
        var partitions = new ArrayList<List<Integer>>();
        for (int n4 = totalPracticePeriods / 4; n4 >= 0; n4--) {
            int rem = totalPracticePeriods - 4 * n4;
            if (rem % 3 == 0) {
                int n3 = rem / 3;
                var sessionLengths = new ArrayList<Integer>(n4 + n3);
                for (int i = 0; i < n4; i++) sessionLengths.add(4);
                for (int i = 0; i < n3; i++) sessionLengths.add(3);
                partitions.add(Collections.unmodifiableList(sessionLengths));
            }
        }
        return partitions;
    }

    /**
     * 3.3. Kiểm tra khoảng cách ngày giữa ngày đề xuất và tất cả các ngày đã có của cùng nhóm TH.
     */
    public static boolean satisfiesPracticeGap(Collection<LocalDate> existingDates, LocalDate candidateDate, int minGapDays) {
        for (LocalDate existing : existingDates) {
            long diff = Math.abs(existing.toEpochDay() - candidateDate.toEpochDay());
            if (diff < minGapDays) {
                return false;
            }
        }
        return true;
    }

    /**
     * Đếm số cặp ngày liên tiếp của một nhóm TH có khoảng cách < PREFERRED_GAP_DAYS (14 ngày).
     */
    public static int countSubPreferredGaps(Collection<LocalDate> dates) {
        if (dates == null || dates.size() <= 1) return 0;
        var sorted = new ArrayList<>(dates);
        Collections.sort(sorted);
        int subPreferred = 0;
        for (int i = 1; i < sorted.size(); i++) {
            long gap = sorted.get(i).toEpochDay() - sorted.get(i - 1).toEpochDay();
            if (gap < PREFERRED_GAP_DAYS) {
                subPreferred++;
            }
        }
        return subPreferred;
    }

    /**
     * 3.4. Xác định các buổi (0 = Sáng < 12:00, 1 = Trưa 12:00-13:00, 2 = Chiều >= 13:00)
     * mà một ca học [start, end) chiếm giữ. Khung đi qua ranh giới được tính vào mọi buổi nó chiếm.
     */
    public static List<Integer> occupiedHalfDays(LocalTime start, LocalTime end) {
        var halfDays = new ArrayList<Integer>(3);
        if (start.isBefore(NOON_START)) {
            halfDays.add(0); // Sáng
        }
        if (start.isBefore(AFTERNOON_START) && end.isAfter(NOON_START)) {
            halfDays.add(1); // Trưa (Tiết 6: 12:00 - 13:00)
        }
        if (end.isAfter(AFTERNOON_START) || !start.isBefore(AFTERNOON_START)) {
            halfDays.add(2); // Chiều
        }
        return halfDays;
    }
}