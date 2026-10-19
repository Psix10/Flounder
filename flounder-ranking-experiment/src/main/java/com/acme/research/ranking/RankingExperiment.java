package com.acme.research.ranking;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.results.infrastructure.jpa.*;
@Service
public class RankingExperiment {
 private final JdbcTemplate jdbc;
 private final CompetitionUnitEntryRepository entries;
 private final ResultRepository results;
 public RankingExperiment(JdbcTemplate jdbc, CompetitionUnitEntryRepository entries, ResultRepository results) {
  this.jdbc=jdbc; this.entries=entries; this.results=results;
 }
 @Transactional
 public void execute(String variant, UUID unitId, String strategy) {
  boolean descending=NumericContract.descending(strategy);
  if (!"JAVA".equals(variant) && !"SQL".equals(variant)) throw new IllegalArgumentException(variant);
  guard(unitId);
  OffsetDateTime now=OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  if ("JAVA".equals(variant)) javaRank(unitId,descending,now);
  else sqlRank(unitId,descending,now);
 }
 private void guard(UUID id) {
  List<String> statuses=jdbc.queryForList(
   "select status from competition_units where id=? for update /* rank-unit-lock */",String.class,id);
  if (statuses.isEmpty()) throw new RuleFailure("results.unit_not_found");
  if ("PUBLISHED".equals(statuses.getFirst())) throw new RuleFailure("results.unit_published");
  jdbc.queryForObject("""
   select count(*) from (
    select id from competition_unit_entries where competition_unit_id=?
    order by id for update
   ) locked /* rank-entry-lock */
   """, Long.class,id);
  jdbc.queryForObject("""
   select count(*) from (
    select r.id from results r join competition_unit_entries e on e.id=r.competition_unit_entry_id
    where e.competition_unit_id=? order by r.id for update of r
   ) locked /* rank-result-lock */
   """, Long.class,id);
 }
 private void javaRank(UUID unitId, boolean descending, OffsetDateTime now) {
  List<UUID> ids=entries.findByCompetitionUnitId(unitId).stream().map(CompetitionUnitEntryEntity::getId).toList();
  List<ResultEntity> all=ids.isEmpty()?List.of():results.findByCompetitionUnitEntryIdIn(ids);
  boolean invalid=all.stream().filter(r->NumericContract.rankable(r.getStatus()))
   .anyMatch(r->NumericContract.parse(r.getRawValue())==null);
  if (invalid) throw new RuleFailure("results.invalid_raw_value");
  for (ResultEntity r:all) if (NumericContract.rankable(r.getStatus())) {
   r.setFinalPlace(null); r.setUpdatedAt(now);
  }
  Comparator<Ranked> comparator=Comparator.comparing(Ranked::value);
  if (descending) comparator=comparator.reversed();
  comparator=comparator.thenComparing(r->r.result().getId().toString());
  List<Ranked> sorted=all.stream().filter(r->NumericContract.rankable(r.getStatus()))
   .map(r->new Ranked(r,NumericContract.parse(r.getRawValue()))).sorted(comparator).toList();
  int place=1;
  for (Ranked ranked:sorted) {
   ResultEntity r=ranked.result(); r.setFinalPlace(place++); r.setStatus("VALID"); r.setUpdatedAt(now);
  }
  results.saveAll(all);
 }
 private void sqlRank(UUID unitId, boolean descending, OffsetDateTime now) {
  String direction=descending?"DESC":"ASC";
  String query="""
   with parsed as materialized (
    select r.id, experiment_numeric(r.raw_value) as value
    from results r join competition_unit_entries e on e.id=r.competition_unit_entry_id
    where e.competition_unit_id=? and r.status in ('PENDING','VALID')
   ), invalid as (
    select count(*) as n from parsed where value is null
   ), ranked as (
    select id,row_number() over(order by value %s,id::text collate "C" asc)::integer as place
    from parsed where value is not null
   ), updated as (
    update results r set final_place=k.place,status='VALID',updated_at=?,version=r.version+1
    from ranked k,invalid i where r.id=k.id and i.n=0 returning r.id
   ) select (select n from invalid) as invalid_count,(select count(*) from updated) as updated_count
   """.formatted(direction);
  Long invalid=jdbc.queryForObject(query,(rs,row)->rs.getLong("invalid_count"),unitId,now);
  if (invalid!=null && invalid>0) throw new RuleFailure("results.invalid_raw_value");
 }
 private record Ranked(ResultEntity result,Double value) {}
}
