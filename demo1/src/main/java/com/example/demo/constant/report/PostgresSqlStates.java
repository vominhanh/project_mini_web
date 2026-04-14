package com.example.demo.constant.report;

/**
 * Mã SQLState Postgres thường gặp (xử lý lỗi unique constraint, v.v.).
 */
public final class PostgresSqlStates {

    /** unique_violation */
    public static final String UNIQUE_VIOLATION = "23505";

    private PostgresSqlStates() {
    }
}
