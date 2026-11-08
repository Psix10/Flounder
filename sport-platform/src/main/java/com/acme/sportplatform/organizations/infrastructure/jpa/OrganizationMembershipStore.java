package com.acme.sportplatform.organizations.infrastructure.jdbc;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import com.acme.sportplatform.organizations.api.OrganizationMemberResponse;
@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class OrganizationMembershipStore {
    private final JdbcTemplate jdbc;
    public OrganizationMembershipStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean lockOrganization(UUID id) {
        return !jdbc.query("SELECT id FROM organizations WHERE id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),id).isEmpty();
    }
    public boolean organizationExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM organizations WHERE id=?)",Boolean.class,id));
    }
    public Optional<OrganizationMemberResponse> find(UUID organization,UUID user) {
        return jdbc.query("SELECT * FROM organization_members WHERE organization_id=? AND user_id=?",this::row,organization,user).stream().findFirst();
    }
    public Optional<OrganizationMemberResponse> insert(UUID organization,UUID user,String role,UUID actor) {
        return jdbc.query("""
                INSERT INTO organization_members (id,organization_id,user_id,member_role,created_at,assigned_by_user_id)
                VALUES (?,?,?,?,clock_timestamp(),?)
                ON CONFLICT (organization_id,user_id) DO NOTHING
                RETURNING *
                """,this::row,UUID.randomUUID(),organization,user,role,actor).stream().findFirst();
    }
    public List<OrganizationMemberResponse> list(UUID organization) {
        return jdbc.query("SELECT * FROM organization_members WHERE organization_id=? ORDER BY created_at,id",this::row,organization);
    }
    private OrganizationMemberResponse row(ResultSet rs,int index) throws SQLException {
        return new OrganizationMemberResponse(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),
                rs.getObject("user_id",UUID.class),rs.getString("member_role"),rs.getObject("created_at",OffsetDateTime.class),
                rs.getObject("assigned_by_user_id",UUID.class));
    }
}
