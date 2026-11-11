package com.security;

/** departmentId is set for teachers and department heads that belong to a department. */
public record AccountPrincipal(String teacherId, String username, String role, Long departmentId) {
    public AccountPrincipal(String teacherId, String username, String role) {
        this(teacherId, username, role, null);
    }

    public boolean admin() {
        return "ADMIN".equals(role);
    }

    public boolean departmentHead() {
        return "DEPT_HEAD".equals(role) && departmentId != null;
    }
}
