package com.acme.research.ranking;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.acme.sportplatform.common.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static com.acme.research.ranking.RankingExperiment.Variant;

@SpringJUnitConfig(RankingSliceConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class RankingComparisonTest {
    @Autowired RankingExperiment experiment;
    @Autowired RankingFixtures f;

    @ParameterizedTest @EnumSource(Variant.class)
    void ascending(Variant v) {
        var unit=f.create("01:02.00","00:58.42","60.00");
        experiment.execute(v,unit,"asc");
        assertEquals(List.of(3,1,2),f.places());
        assertEquals(1L,unitVersion());
        assertTrue(f.snapshot().stream().allMatch(r->"VALID".equals(r.get("status"))));
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void descending(Variant v) {
        var unit=f.create("01:02.00","00:58.42","60.00");
        experiment.execute(v,unit,"DESC");
        assertEquals(List.of(1,3,2),f.places());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void tiesAndZeroHaveDeterministicOrder(Variant v) {
        var unit=f.create("00:58.42","58.42","58.420");
        experiment.execute(v,unit,"ASC");
        assertEquals(List.of(1,2,3),f.places());
        f.reset();
        experiment.execute(v,unit,"DESC");
        assertEquals(List.of(1,2,3),f.places());
        unit=f.create("-0.0","0","0.0");
        experiment.execute(v,unit,"ASC");
        assertEquals(List.of(1,2,3),f.places());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void specialStatusesRemainUntouched(Variant v) {
        var unit=f.create("10","bad",null,"bad","bad");
        String[] statuses={"DNS","DNF","DSQ","DISQUALIFIED"};
        for (int i=0;i<statuses.length;i++) f.status(i+1,statuses[i],77);
        var before=f.snapshot();
        experiment.execute(v,unit,"ASC");
        var after=f.snapshot();
        assertEquals(1,f.places().get(0));
        for (int i=1;i<after.size();i++) assertEquals(before.get(i),after.get(i));
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void invalidPendingRollsBack(Variant v) {
        var unit=f.create("10","00:60.00");
        var before=f.snapshot();
        assertBusiness("results.invalid_raw_value",()->experiment.execute(v,unit,"ASC"));
        assertEquals(before,f.snapshot());
        assertEquals(0L,unitVersion());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void invalidValidRollsBack(Variant v) {
        var unit=f.create("10","NaN");
        f.status(1,"VALID",2);
        var before=f.snapshot();
        assertBusiness("results.invalid_raw_value",()->experiment.execute(v,unit,"ASC"));
        assertEquals(before,f.snapshot());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void publishedUnitRejected(Variant v) {
        var unit=f.create("10","20");
        f.jdbc.update("UPDATE competition_units SET status='PUBLISHED' WHERE id=?",unit);
        var before=f.snapshot();
        assertBusiness("results.unit_published",()->experiment.execute(v,unit,"ASC"));
        assertEquals(before,f.snapshot());
        assertEquals(0L,unitVersion());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void missingUnitAndInvalidStrategy(Variant v) {
        f.create();
        assertBusiness("results.unit_not_found",()->experiment.execute(v,UUID.randomUUID(),"ASC"));
        assertBusiness("results.invalid_ranking_strategy",()->experiment.execute(v,UUID.randomUUID(),"sideways"));
        assertBusiness("results.invalid_ranking_strategy",()->experiment.execute(v,UUID.randomUUID(),null));
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void emptyUnitAndEntryWithoutResult(Variant v) {
        var unit=f.create();
        experiment.execute(v,unit,"ASC");
        assertTrue(f.snapshot().isEmpty());
        unit=f.create("10");
        f.jdbc.update("DELETE FROM results");
        experiment.execute(v,unit,"ASC");
        assertTrue(f.snapshot().isEmpty());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void rerunAndCorrection(Variant v) {
        var unit=f.create("10","20","30");
        experiment.execute(v,unit,"ASC");
        experiment.execute(v,unit,"ASC");
        assertEquals(List.of(1,2,3),f.places());
        assertTrue(f.snapshot().stream().allMatch(r->((Number)r.get("version")).longValue()==2));
        f.jdbc.update("UPDATE results SET raw_value='40',version=version+1 WHERE id=?",f.resultId(0));
        experiment.execute(v,unit,"ASC");
        assertEquals(List.of(3,1,2),f.places());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void failureAfterWritesRollsBack(Variant v) {
        var unit=f.create("10","20");
        var before=f.snapshot();
        assertThrows(IllegalStateException.class,()->experiment.execute(v,unit,"ASC",()->{},()->{
            throw new IllegalStateException("injected after flush/update");
        }));
        assertEquals(before,f.snapshot());
        assertEquals(0L,unitVersion());
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void unrelatedUnitIsNotModified(Variant v) {
        var unit=f.create("10");
        UUID other=new UUID(5,2), entry=new UUID(1,100), result=new UUID(0,100);
        f.jdbc.update("INSERT INTO competition_units(id,event_discipline_id,label,sequence_number) VALUES (?,?,'Other',2)",other,RankingFixtures.DISCIPLINE);
        f.jdbc.update("INSERT INTO competition_unit_entries(id,competition_unit_id,registration_id) VALUES (?,?,?)",entry,other,f.id(4,1));
        f.jdbc.update("INSERT INTO results(id,competition_unit_entry_id,raw_value,result_type,status,final_place) VALUES (?,?,'5','TIME','VALID',99)",result,entry);
        var before=f.snapshot().get(1);
        experiment.execute(v,unit,"ASC");
        assertEquals(before,f.snapshot().get(1));
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void concurrentResultEditIsNotOverwritten(Variant v) throws Exception {
        conflict(v,false);
    }
    @ParameterizedTest @EnumSource(Variant.class)
    void concurrentPublicationRejectsRecalculation(Variant v) throws Exception {
        conflict(v,true);
    }
    private void conflict(Variant v, boolean publication) throws Exception {
        var unit=f.create("10","20");
        CountDownLatch read=new CountDownLatch(1), resume=new CountDownLatch(1);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> task=executor.submit(()->{
                try {
                    experiment.execute(v,unit,"ASC",()->{
                        read.countDown();
                        try { if (!resume.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    });
                    return null;
                } catch (Throwable failure) { return failure; }
            });
            assertTrue(read.await(15,TimeUnit.SECONDS),"worker did not reach validation barrier");
            if (publication) {
                f.jdbc.update("UPDATE competition_units SET status='PUBLISHED',version=version+1 WHERE id=?",unit);
            } else {
                f.jdbc.update("UPDATE results SET raw_value='40',version=version+1 WHERE id=?",f.resultId(0));
            }
            resume.countDown();
            Throwable failure=task.get(20,TimeUnit.SECONDS);
            assertInstanceOf(BusinessException.class,failure);
            assertEquals("results.concurrent_update",((BusinessException)failure).getCode());
            assertTrue(f.places().stream().allMatch(Objects::isNull));
            if (!publication) assertEquals("40",f.snapshot().get(0).get("raw_value"));
            else assertEquals("PUBLISHED",f.jdbc.queryForObject("SELECT status FROM competition_units WHERE id=?",String.class,unit));
        } finally {
            resume.countDown(); executor.shutdownNow(); executor.awaitTermination(20,TimeUnit.SECONDS);
        }
    }
    @ParameterizedTest @MethodSource("parserExamples")
    void numericContractMatchesSql(String value, Double expected) {
        Double javaValue=NumericContract.parse(value);
        Double sqlValue=f.jdbc.queryForObject("SELECT ranking_experiment.numeric_value(?)",Double.class,value);
        assertEquals(expected,javaValue);
        assertEquals(expected,sqlValue);
    }
    static Stream<Arguments> parserExamples() {
        return Stream.of(
                Arguments.of("58.42",58.42),Arguments.of("00:58.42",58.42),
                Arguments.of("1:00",60d),Arguments.of("1.5:30",120d),
                Arguments.of("-1",-1d),Arguments.of("+1",1d),Arguments.of("-0.0",0d),
                Arguments.of(null,null),Arguments.of("",null),Arguments.of("NaN",null),
                Arguments.of("Infinity",null),Arguments.of("-Infinity",null),
                Arguments.of("00:60",null),Arguments.of("00:58:",null),
                Arguments.of("-1:10",null),Arguments.of("1:-1",null),
                Arguments.of("1e2",null),Arguments.of("0x1.0p0",null),
                Arguments.of("1D",null),Arguments.of(" 10 ",null),Arguments.of(".5",null));
    }
    @Test
    void benchmark() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ranking.benchmark"),"enable with -Dranking.benchmark=true");
        int warmup=Integer.getInteger("ranking.warmup",10);
        int repetitions=Integer.getInteger("ranking.repetitions",100);
        assertTrue(warmup>=0 && repetitions>=20);
        Path out=Path.of("target","ranking-experiment","batch-"+Integer.getInteger("ranking.batchSize",0)); Files.createDirectories(out);
        List<String> samples=new ArrayList<>();
        samples.add("n,variant,iteration,elapsed_ns,jdbc_execute_calls,jdbc_batch_items,jdbc_result_rows_read");
        List<String> summary=new ArrayList<>();
        summary.add("n,variant,repetitions,median_ms,p25_ms,p75_ms,p95_ms,mean_jdbc_execute_calls,mean_jdbc_result_rows_read");
        for (String size:System.getProperty("ranking.sizes","6,8,10,100").split(",")) {
            int n=Integer.parseInt(size.strip()); assertTrue(n>0);
            String[] values=new String[n];
            for (int i=0;i<n;i++) values[i]=String.format(Locale.ROOT,"%.2f",20+(n-i)*0.01);
            UUID unit=f.create(values);
            f.jdbc.execute("ANALYZE results; ANALYZE competition_unit_entries; ANALYZE competition_units");
            Map<Variant,List<Observation>> observed=new EnumMap<>(Variant.class);
            for (Variant v:Variant.values()) observed.put(v,new ArrayList<>());
            for (int iteration=-warmup;iteration<repetitions;iteration++) {
                Variant[] order=iteration%2==0 ? new Variant[]{Variant.JAVA,Variant.SQL}
                        : new Variant[]{Variant.SQL,Variant.JAVA};
                for (Variant v:order) {
                    f.reset();
                    JdbcMetrics.Sample counters=JdbcMetrics.start();
                    long start=System.nanoTime();
                    long elapsed;
                    try { experiment.execute(v,unit,"ASC"); elapsed=System.nanoTime()-start; }
                    finally { JdbcMetrics.stop(); }
                    List<Integer> expected=new ArrayList<>();
                    for (int i=n;i>=1;i--) expected.add(i);
                    assertEquals(expected,f.places());
                    assertTrue(f.snapshot().stream().allMatch(r->"VALID".equals(r.get("status"))));
                    if (iteration>=0) {
                        observed.get(v).add(new Observation(elapsed,counters.executeCalls,counters.rowsRead));
                        samples.add(String.format(Locale.ROOT,"%d,%s,%d,%d,%d,%d,%d",n,v,iteration,
                                elapsed,counters.executeCalls,counters.batchItems,counters.rowsRead));
                    }
                }
            }
            for (Variant v:Variant.values()) {
                var obs=observed.get(v);
                double[] times=obs.stream().mapToDouble(o->o.nanos/1_000_000d).sorted().toArray();
                summary.add(String.format(Locale.ROOT,"%d,%s,%d,%.6f,%.6f,%.6f,%s,%.3f,%.3f",
                        n,v,repetitions,percentile(times,.5),percentile(times,.25),percentile(times,.75),
                        repetitions>=100 ? String.format(Locale.ROOT,"%.6f",percentile(times,.95)) : "",
                        obs.stream().mapToLong(Observation::calls).average().orElseThrow(),
                        obs.stream().mapToLong(Observation::rows).average().orElseThrow()));
            }
        }
        Files.write(out.resolve("samples.csv"),samples,StandardCharsets.UTF_8);
        Files.write(out.resolve("summary.csv"),summary,StandardCharsets.UTF_8);
        Files.writeString(out.resolve("environment.txt"),String.join("\n",
                "java="+System.getProperty("java.version"),"os="+System.getProperty("os.name"),
                "arch="+System.getProperty("os.arch"),"postgres="+f.jdbc.queryForObject("SELECT version()",String.class),
                "hibernate="+org.hibernate.Version.getVersionString(),"batch_size="+Integer.getInteger("ranking.batchSize",0),
                "warmup="+warmup,"repetitions="+repetitions,
                "sizes="+System.getProperty("ranking.sizes","6,8,10,100"),
                "isolation=REPEATABLE_READ; common OPTIMISTIC_FORCE_INCREMENT on unit",
                "time=transaction begin through commit; counters=JDBC methods, not network packets",
                "schema=copied production migrations in dedicated ephemeral PostgreSQL; not full app startup"),StandardCharsets.UTF_8);
    }
    private static double percentile(double[] sorted,double p) {
        return sorted[Math.max(0,(int)Math.ceil(p*sorted.length)-1)];
    }
    private long unitVersion() {
        return f.jdbc.queryForObject("SELECT version FROM competition_units WHERE id=?",Long.class,RankingFixtures.UNIT);
    }
    private static void assertBusiness(String code,org.junit.jupiter.api.function.Executable executable) {
        BusinessException e=assertThrows(BusinessException.class,executable);
        assertEquals(code,e.getCode());
    }
    private record Observation(long nanos,long calls,long rows) {}
}
