package com.dto;

import java.util.List;

public record PeriodTemplateRequest(String schoolCode, List<PeriodTimeDTO> periods) {}
