package com.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** Lịch bận của giảng viên (thứ bận cố định trong tuần hoặc ngày bận cụ thể), dùng khi phân công / xếp lớp. */
@Entity
@Table(name = "teacher_unavailability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", nullable = false, length = 50)
    @Builder.Default
    private String schoolCode = "PTIT";

    @Column(name = "teacher_id", nullable = false, length = 50)
    private String teacherId;

    /** 2 = Thứ 2 ... 8 = Chủ Nhật; null nếu khai báo theo ngày cụ thể. */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /** Ngày bận cụ thể (YYYY-MM-DD); null nếu khai báo theo thứ cố định trong tuần. */
    @Column(name = "specific_date")
    private LocalDate specificDate;

    @Column(name = "note", length = 255)
    private String note;
}
