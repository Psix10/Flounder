package com.acme.research.ranking;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

final class RankingFixtures {
    static final UUID UNIT = new UUID(5,1);
    static final UUID DISCIPLINE = new UUID(6,1);
    static final String OLD = "2000-01-01T00:00:00Z";
    final JdbcTemplate jdbc;
    RankingFixtures(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    UUID id(long family, long value) { return new UUID(family,value); }
    UUID resultId(int index) { return id(0,index+1); }
    UUID create(String... values) {
        jdbc.execute("""
            TRUNCATE results, competition_unit_entries, competition_units, registrations,
              event_disciplines, events, regulation_versions, regulation_templates,
              discipline_templates, profiles, users, organizations, venues, sports CASCADE
            """);
        UUID owner=id(10,1), org=id(11,1), venue=id(12,1), sport=id(13,1),
                template=id(14,1), regulation=id(15,1), version=id(16,1), event=id(17,1);
        user(owner,"owner@ranking.test");
        jdbc.update("INSERT INTO organizations(id,type,name,created_at,updated_at) VALUES (?,'club','Research',now(),now())",org);
        jdbc.update("INSERT INTO venues(id,name,timezone) VALUES (?,'Research','UTC')",venue);
        jdbc.update("INSERT INTO sports(id,code,name) VALUES (?,'ranking_research','Research')",sport);
        jdbc.update("""
            INSERT INTO discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy)
            VALUES (?,?,'research','Research','individual','heat','TIME','ASC')
            """,template,sport);
        jdbc.update("""
            INSERT INTO regulation_templates(id,sport_id,code,name,created_at)
            VALUES (?,?,'research','Research',now())
            """,regulation,sport);
        jdbc.update("""
            INSERT INTO regulation_versions(id,regulation_template_id,version_no,status,rules_json,created_at)
            VALUES (?,?,1,'published','{}',now())
            """,version,regulation);
        jdbc.update("""
            INSERT INTO events(id,organization_id,venue_id,sport_id,regulation_version_id,title,
              event_start_at,event_end_at,status,public_slug,created_at,updated_at,created_by_user_id)
            VALUES (?,?,?,?,?,'Research',now(),now()+interval '1 day','DRAFT','ranking-research',now(),now(),?)
            """,event,org,venue,sport,version,owner);
        jdbc.update("""
            INSERT INTO event_disciplines(id,event_id,discipline_template_id,code,name,
              competition_format,unit_type,result_type,ranking_strategy,status,created_at,updated_at)
            VALUES (?,?,?,'research','Research','individual','heat','TIME','ASC','DRAFT',now(),now())
            """,DISCIPLINE,event,template);
        jdbc.update("INSERT INTO competition_units(id,event_discipline_id,label,sequence_number) VALUES (?,?,'Research',1)",UNIT,DISCIPLINE);
        for (int i=0;i<values.length;i++) {
            UUID u=id(2,i+1), profile=id(3,i+1), registration=id(4,i+1), entry=id(1,i+1);
            user(u,"athlete"+i+"@ranking.test");
            jdbc.update("""
                INSERT INTO profiles(id,user_id,first_name,last_name,birth_date,gender)
                VALUES (?,?,'Research','Athlete','2000-01-01','male')
                """,profile,u);
            jdbc.update("""
                INSERT INTO registrations(id,event_id,event_discipline_id,participant_user_id,
                  participant_profile_id,status,participant_snapshot,created_at,updated_at)
                VALUES (?,?,?,?,?,'CONFIRMED','{}',now(),now())
                """,registration,event,DISCIPLINE,u,profile);
            jdbc.update("INSERT INTO competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position) VALUES (?,?,?,?)",
                    entry,UNIT,registration,i+1);
            jdbc.update("""
                INSERT INTO results(id,competition_unit_entry_id,raw_value,result_type,status,updated_at)
                VALUES (?,?,?,'TIME','PENDING',?::timestamptz)
                """,resultId(i),entry,values[i],OLD);
        }
        return UNIT;
    }
    private void user(UUID id,String email) {
        jdbc.update("INSERT INTO users(id,email,password_hash,status) VALUES (?,?,'test-only','active')",id,email);
    }
    void reset() {
        jdbc.update("UPDATE results SET final_place=NULL,status='PENDING',version=0,updated_at=?::timestamptz",OLD);
        jdbc.update("UPDATE competition_units SET status='DRAFT',version=0");
    }
    void status(int index,String status,Integer place) {
        jdbc.update("UPDATE results SET status=?,final_place=? WHERE id=?",status,place,resultId(index));
    }
    List<Map<String,Object>> snapshot() {
        return jdbc.queryForList("SELECT id,raw_value,status,final_place,version,updated_at FROM results ORDER BY id");
    }
    List<Integer> places() {
        return jdbc.query("SELECT final_place FROM results ORDER BY id",(rs,n)->(Integer)rs.getObject(1));
    }
}
