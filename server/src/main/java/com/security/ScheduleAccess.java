package com.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ScheduleAccess {
    private AccountPrincipal account() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account ? account : null;
    }

    public void requireTeacherAccess(String teacherId) {
        var account = account();
        if (account == null || (!account.admin() && !account.teacherId().equals(teacherId))) {
            throw new AccessDeniedException("Bạn không có quyền truy cập lịch của giảng viên này.");
        }
    }

    public boolean isAdmin() {
        var account = account();
        return account != null && account.admin();
    }

    public void requireAdmin() {
        if (!isAdmin()) throw new AccessDeniedException("Chỉ quản trị khoa được thực hiện thao tác này.");
    }

    public boolean isSelf(String teacherId) {
        var account = account();
        return account != null && account.teacherId().equals(teacherId);
    }


    public Long headDepartmentId() {
        var account = account();
        return account != null && account.departmentHead() ? account.departmentId() : null;
    }

    public void requireDepartmentAccess(Long departmentId) {
        if (isAdmin()) return;
        Long own = headDepartmentId();
        if (own == null || departmentId == null || !own.equals(departmentId)) {
            throw new AccessDeniedException("Bạn chỉ được thao tác trên bộ môn của mình.");
        }
    }
}
