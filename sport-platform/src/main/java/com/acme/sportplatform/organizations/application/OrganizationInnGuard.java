package com.acme.sportplatform.organizations.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;

@Component
public class OrganizationInnGuard {

    private final JdbcTemplate jdbc;

    public OrganizationInnGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String requireAvailable(String value) {
        String inn = normalize(value);

        if (inn == null) {
            return null;
        }

        Boolean exists = jdbc.queryForObject(
                """
                select exists (
                    select 1
                    from organizations
                    where btrim(inn) = ?
                )
                """,
                Boolean.class,
                inn
        );

        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(
                    "organizations.inn_already_registered",
                    "Организация с таким ИНН уже зарегистрирована. "
                            + "Повторная регистрация не требуется."
            );
        }

        return inn;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        // Match PostgreSQL btrim(): remove ordinary boundary spaces.
        int start = 0;
        int end = value.length();

        while (start < end && value.charAt(start) == ' ') {
            start++;
        }

        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }

        return start == end ? null : value.substring(start, end);
    }
}