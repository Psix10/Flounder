package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.projection.UserRoleCodeProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {

    @Query("""
            select r.code as code
            from UserRoleAssignmentEntity ura
            join RoleEntity r on r.id = ura.roleId
            where ura.userId = :userId
            """)
    List<UserRoleCodeProjection> findRoleCodesByUserId(UUID userId);
}