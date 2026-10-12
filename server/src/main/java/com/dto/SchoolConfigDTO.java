package com.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolConfigDTO {
    private String schoolId;
    private String schoolName;
    private String colorCode;
    private List<PeriodTimeDTO> periods;
}