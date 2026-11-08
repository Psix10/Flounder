package com.acme.sportplatform.identity.application;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.acme.sportplatform.identity.UserAccountLookup;
@Service
public class UserAccountLookupService implements UserAccountLookup {
    private final JdbcTemplate jdbc;
    public UserAccountLookupService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Account> lock(UUID userId) {
        if(userId==null) return Optional.empty();
        return jdbc.query("SELECT id,status FROM users WHERE id=? FOR SHARE",
                (rs,n)->new Account(rs.getObject("id",UUID.class),rs.getString("status")),userId).stream().findFirst();
    }
}
