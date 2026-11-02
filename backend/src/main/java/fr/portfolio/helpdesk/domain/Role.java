package fr.portfolio.helpdesk.domain;

public enum Role {
    USER, TECHNICIAN, ADMIN;

    public boolean isStaff() {
        return this == TECHNICIAN || this == ADMIN;
    }
}
