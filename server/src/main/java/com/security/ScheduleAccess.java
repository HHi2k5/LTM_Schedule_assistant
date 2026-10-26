package com.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ScheduleAccess {
    public void requireTeacherAccess(String teacherId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal account)
                || (!"ADMIN".equals(account.role()) && !account.teacherId().equals(teacherId))) {
            throw new AccessDeniedException("Bạn không có quyền truy cập lịch của giảng viên này.");
        }
    }
}
