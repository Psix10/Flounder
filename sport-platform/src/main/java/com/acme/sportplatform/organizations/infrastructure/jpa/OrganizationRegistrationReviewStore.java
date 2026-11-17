package com.acme.sportplatform.organizations.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.organizations.api.OrganizationRegistrationReviewResponse;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OrganizationRegistrationReviewStore {

    private final JdbcTemplate jdbc;

    public OrganizationRegistrationReviewStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrganizationRegistrationReviewResponse> lock(UUID id) {
        return jdbc.query(
                """
                SELECT *
                FROM organization_registration_requests
                WHERE id = ?
                FOR UPDATE
                """,
                this::row,
                id
        ).stream().findFirst();
    }

    public List<OrganizationRegistrationReviewResponse> list(
            String status,
            int limit,
            int offset
    ) {
        return jdbc.query(
                """
                SELECT *
                FROM organization_registration_requests
                WHERE status = ?
                ORDER BY created_at, id
                LIMIT ? OFFSET ?
                """,
                this::row,
                status,
                limit,
                offset
        );
    }

    public List<OrganizationRegistrationReviewResponse> listForApplicant(
            UUID applicant,
            int limit,
            int offset
    ) {
        return jdbc.query(
                """
                SELECT *
                FROM organization_registration_requests
                WHERE applicant_user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                this::row,
                applicant,
                limit,
                offset
        );
    }

    public OrganizationRegistrationReviewResponse complete(
            UUID id,
            String status,
            String reason,
            UUID organizationId,
            UUID actor,
            OffsetDateTime now
    ) {
        var updated = jdbc.query(
                """
                UPDATE organization_registration_requests
                SET status = ?,
                    rejection_reason = ?,
                    organization_id = ?,
                    reviewed_by_user_id = ?,
                    reviewed_at = ?,
                    updated_at = ?
                WHERE id = ? AND status = 'PENDING'
                RETURNING *
                """,
                this::row,
                status,
                reason,
                organizationId,
                actor,
                now,
                now,
                id
        );

        if (updated.size() != 1) {
            throw new IllegalStateException(
                    "Locked pending registration was not updated"
            );
        }

        return updated.get(0);
    }

    private OrganizationRegistrationReviewResponse row(
            ResultSet rs,
            int rowNumber
    ) throws SQLException {
        return new OrganizationRegistrationReviewResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("applicant_user_id", UUID.class),
                rs.getString("organization_type"),
                rs.getString("organization_name"),
                rs.getString("legal_name"),
                rs.getString("inn"),
                rs.getString("contact_email"),
                rs.getString("contact_phone"),
                rs.getString("status"),
                rs.getString("rejection_reason"),
                rs.getObject("reviewed_by_user_id", UUID.class),
                rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("organization_id", UUID.class)
        );
    }
}