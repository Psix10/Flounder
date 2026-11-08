package com.acme.sportplatform.identity.application;
import java.util.*;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.acme.sportplatform.identity.UserAccountLookup;
@SuppressWarnings({"unchecked","rawtypes"})
class UserAccountLookupServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UserAccountLookupService service=new UserAccountLookupService(jdbc);
    @Test void nullIdDoesNotQueryStorage() {assertTrue(service.lock(null).isEmpty());verifyNoInteractions(jdbc);}
    @Test void accountActivityIsCaseInsensitiveAndNullSafe() {
        assertTrue(new UserAccountLookup.Account(UUID.randomUUID(),"ACTIVE").active());
        assertFalse(new UserAccountLookup.Account(UUID.randomUUID(),null).active());
        assertFalse(new UserAccountLookup.Account(UUID.randomUUID(),"blocked").active());
    }
    @Test void missingAccountReturnsEmpty() {
        UUID id=UUID.randomUUID();when(jdbc.query(anyString(),any(RowMapper.class),eq(id))).thenReturn(List.of());assertTrue(service.lock(id).isEmpty());
    }
    @Test void mapsOnlyPublicAccountStateAndRequestsShareLock() throws Exception {
        UUID id=UUID.randomUUID();var rs=mock(ResultSet.class);when(rs.getObject("id",UUID.class)).thenReturn(id);when(rs.getString("status")).thenReturn("active");
        when(jdbc.query(contains("FOR SHARE"),any(RowMapper.class),eq(id))).thenAnswer(call->{
            RowMapper<UserAccountLookup.Account> mapper=call.getArgument(1);return List.of(mapper.mapRow(rs,0));
        });
        assertEquals(new UserAccountLookup.Account(id,"active"),service.lock(id).orElseThrow());
    }
}
