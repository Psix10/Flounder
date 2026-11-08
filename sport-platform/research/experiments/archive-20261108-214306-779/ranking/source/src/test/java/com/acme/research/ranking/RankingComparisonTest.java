package com.acme.research.ranking;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.acme.sportplatform.results.infrastructure.jpa.*;

@SpringBootTest(classes=RankingComparisonTest.Config.class,properties={
 "spring.jpa.hibernate.ddl-auto=validate","spring.sql.init.mode=always",
 "spring.sql.init.schema-locations=classpath:research-schema.sql","spring.sql.init.separator=@@",
 "spring.jpa.open-in-view=false","spring.jpa.show-sql=false",
 "spring.jpa.properties.hibernate.jdbc.batch_size=${research.batch:0}",
 "spring.jpa.properties.hibernate.order_updates=true","logging.level.root=WARN"
})
@Testcontainers
class RankingComparisonTest {
 @SpringBootConfiguration
 @EnableAutoConfiguration
 @EntityScan(basePackageClasses=ResultEntity.class)
 @EnableJpaRepositories(basePackageClasses=ResultRepository.class)
 @Import(RankingExperiment.class)
 static class Config {
  @Bean static BeanPostProcessor meter() {
   return new BeanPostProcessor() {
    @Override public Object postProcessAfterInitialization(Object bean,String name) {
     return bean instanceof DataSource ds && !(bean instanceof JdbcMeter) ? new JdbcMeter(ds) : bean;
    }
   };
  }
 }
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16")
  .withDatabaseName("ranking_research").withUsername("research").withPassword("research");
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
  registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
  registry.add("spring.datasource.username",POSTGRES::getUsername);
  registry.add("spring.datasource.password",POSTGRES::getPassword);
 }
 @Autowired JdbcTemplate jdbc;
 @Autowired RankingExperiment experiment;
 @Autowired PlatformTransactionManager manager;
 static final UUID UNIT=UUID.fromString("10000000-0000-0000-0000-000000000001");
 static final UUID DISCIPLINE=UUID.fromString("20000000-0000-0000-0000-000000000001");
 static final OffsetDateTime BASE=OffsetDateTime.parse("2000-01-01T00:00:00Z");
 final List<String> checks=new ArrayList<>();
 record Seed(String raw,String status,Integer oldPlace) {}
 record State(UUID id,String raw,String status,Integer place,long version,OffsetDateTime created,OffsetDateTime updated) {}
 record Sample(int n,String strategy,String variant,int iteration,String order,long ns) {}
 static UUID id(int index) { return UUID.fromString("00000000-0000-0000-0000-"+String.format(Locale.ROOT,"%012x",index+1)); }
 static Seed pending(String raw) { return new Seed(raw,"PENDING",null); }
 Path output;

 @Test void verifyThenMeasure() throws Exception {
  String batch=System.getProperty("research.batch","0");
  String run=java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
  output=Path.of("target","research","batch-"+batch,run);
  Files.createDirectories(output);
  try {
   verifyParsers();
   for(String variant:List.of("JAVA","SQL")) verifyVariant(variant);
   writeChecks();
   if(Boolean.parseBoolean(System.getProperty("research.benchmark","false"))) benchmark();
   else Files.writeString(output.resolve("status.txt"),"Functional checks completed; benchmark NOT RUN.\n");
  } finally { writeChecks(); }
 }
 void writeChecks() throws Exception {
  Files.write(output.resolve("functional-checks.csv"),
   java.util.stream.Stream.concat(java.util.stream.Stream.of("check,status"),checks.stream()).toList());
 }
 void passed(String name) { checks.add(name+",PASS"); }
 void reset(List<Seed> seeds,String status) {
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   jdbc.execute("truncate results,competition_unit_entries,competition_units,registrations,event_disciplines");
   jdbc.update("insert into event_disciplines(id) values (?)",DISCIPLINE);
   jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,created_at,updated_at) values (?,?,?,1,?,?,?)",
    UNIT,DISCIPLINE,"Research unit",status,BASE,BASE);
   List<Object[]> parents=new ArrayList<>(),entries=new ArrayList<>(),results=new ArrayList<>();
   for(int i=0;i<seeds.size();i++) {
    Seed seed=seeds.get(i); UUID key=id(i);
    parents.add(new Object[]{key});
    entries.add(new Object[]{key,UNIT,key,i+1,BASE});
    results.add(new Object[]{key,key,seed.raw(),"TIME",seed.status(),seed.oldPlace(),BASE,BASE});
   }
   if(!parents.isEmpty()) {
    jdbc.batchUpdate("insert into registrations(id) values (?)",parents);
    jdbc.batchUpdate("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,created_at) values (?,?,?,?,?)",entries);
    jdbc.batchUpdate("insert into results(id,competition_unit_entry_id,raw_value,result_type,status,final_place,created_at,updated_at) values (?,?,?,?,?,?,?,?)",results);
   }
  });
 }
 List<State> states() {
  return jdbc.query("select * from results order by id",(rs,row)->new State(
   rs.getObject("id",UUID.class),rs.getString("raw_value"),rs.getString("status"),
   rs.getObject("final_place",Integer.class),rs.getLong("version"),
   rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class)));
 }
 void error(String variant,UUID unit,String strategy,String expected) {
  RuleFailure failure=assertThrows(RuleFailure.class,()->experiment.execute(variant,unit,strategy));
  assertEquals(expected,failure.getCode());
 }
 void verifyParsers() {
  List<String> raws=new ArrayList<>(Arrays.asList(null,""," ","NaN","Infinity","-Infinity","0x1.0p0","1e3","1D",
   "-1","+1.25","0","-0","-0.00","58.42","00:58.42","1:00","01:02.34","0:0.1","1:60","1:-2",
   "1.5:20","00:58:","1:2:3",":12","1:","12\n","12 ","12,5",".5","1.","9".repeat(50)));
  Random random=new Random(17);
  for(int i=0;i<150;i++) raws.add(String.format(Locale.ROOT,"%d:%02d.%02d",random.nextInt(100),random.nextInt(60),random.nextInt(100)));
  for(int i=0;i<raws.size();i++) {
   String raw=raws.get(i);
   Double expected=NumericContract.parse(raw);
   Double actual=jdbc.queryForObject("select experiment_numeric(?)",Double.class,raw);
   assertEquals(expected,actual,"Parser mismatch: "+raw);
  }
  passed("parser-parity-"+raws.size()+"-inputs");
 }
 void verifyVariant(String variant) throws Exception {
  List<Seed> seeds=List.of(pending("58.42"),pending("00:57.10"),new Seed("00:58.42","VALID",99),
   new Seed(null,"DNS",77),new Seed(null,"DNF",null),new Seed(null,"DSQ",null));
  for(String direction:List.of("ASC","DESC")) {
   reset(seeds,"DRAFT");
   List<State> before=states();
   experiment.execute(variant,UNIT,direction);
   int[] expected=direction.equals("ASC")?new int[]{2,1,3}:new int[]{1,3,2};
   List<State> after=states();
   for(int i=0;i<3;i++) {
    assertEquals(expected[i],after.get(i).place());
    assertEquals("VALID",after.get(i).status());
    assertEquals(1,after.get(i).version());
    assertEquals(BASE,after.get(i).created());
    assertTrue(after.get(i).updated().isAfter(BASE));
   }
   for(int i=3;i<seeds.size();i++) assertEquals(before.get(i),after.get(i));
   experiment.execute(variant,UNIT,direction);
   List<State> repeated=states();
   for(int i=0;i<3;i++) {
    assertEquals(expected[i],repeated.get(i).place()); assertEquals(2,repeated.get(i).version());
   }
   for(int i=3;i<seeds.size();i++) assertEquals(before.get(i),repeated.get(i));
   passed(variant+"-"+direction+"-ties-statuses-repeat-version");
  }
  List<String> invalid=Arrays.asList(null,"","NaN","Infinity","00:58:","01:60","bad","1e3");
  int index=0;
  for(String status:List.of("PENDING","VALID")) for(String raw:invalid) {
   reset(List.of(pending("50"),new Seed(raw,status,9)),"DRAFT");
   List<State> before=states();
   error(variant,UNIT,"ASC","results.invalid_raw_value");
   assertEquals(before,states());
   passed(variant+"-invalid-"+status+"-"+(index++));
  }
  reset(List.of(pending("bad")),"PUBLISHED");
  List<State> before=states();
  error(variant,UNIT,"ASC","results.unit_published"); assertEquals(before,states());
  passed(variant+"-published-priority-and-no-write");
  error(variant,UUID.randomUUID(),"ASC","results.unit_not_found"); passed(variant+"-missing-unit");
  error(variant,UUID.randomUUID(),"INVALID","results.invalid_ranking_strategy"); passed(variant+"-strategy-priority");
  reset(List.of(),"DRAFT"); experiment.execute(variant,UNIT,"ASC"); assertTrue(states().isEmpty());
  passed(variant+"-empty-unit");
  jdbc.update("insert into registrations(id) values (?)",id(0));
  jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id) values (?,?,?)",id(0),UNIT,id(0));
  experiment.execute(variant,UNIT,"ASC"); assertTrue(states().isEmpty()); passed(variant+"-entry-without-result");
  reset(List.of(new Seed(null,"DNS",77)),"DRAFT"); before=states();
  experiment.execute(variant,UNIT,"ASC"); assertEquals(before,states()); passed(variant+"-only-special");
  reset(List.of(pending("-1"),pending("0"),pending("-0.00")),"DRAFT");
  experiment.execute(variant,UNIT,"ASC");
  assertEquals(List.of(1,2,3),states().stream().map(State::place).toList()); passed(variant+"-signed-decimal-zero");
  verifyRollback(variant);
  verifyPublicationRace(variant);
  verifyConcurrentEdit(variant);
 }
 void verifyRollback(String variant) {
  reset(List.of(pending("50"),pending("60"),pending("70")),"DRAFT");
  List<State> before=states();
  jdbc.execute("""
   create function experiment_fail_update() returns trigger language plpgsql as $$
   begin
    if new.id='00000000-0000-0000-0000-000000000003'::uuid then
     raise exception 'research injected update failure';
    end if;
    return new;
   end; $$
   """);
  jdbc.execute("create trigger research_failure before update on results for each row execute function experiment_fail_update()");
  try {
   assertThrows(RuntimeException.class,()->experiment.execute(variant,UNIT,"ASC"));
   assertEquals(before,states()); passed(variant+"-database-error-full-rollback");
  } finally {
   jdbc.execute("drop trigger research_failure on results");
   jdbc.execute("drop function experiment_fail_update()");
  }
 }
 void waitForBlocked(String marker) {
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
  while(System.nanoTime()<end) {
   jdbc.execute("select pg_stat_clear_snapshot()");
   Long count=jdbc.queryForObject("select count(*) from pg_stat_activity where pid<>pg_backend_pid() and wait_event_type='Lock' and query like ?",Long.class,"%"+marker+"%");
   if(count!=null && count>0) return;
   try { Thread.sleep(20); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
  }
  fail("Expected blocked query: "+marker);
 }
 void verifyPublicationRace(String variant) throws Exception {
  reset(List.of(pending("50")),"DRAFT"); List<State> before=states();
  ExecutorService pool=Executors.newSingleThreadExecutor();
  List<Future<?>> future=new ArrayList<>();
  try {
   new TransactionTemplate(manager).executeWithoutResult(tx->{
    jdbc.queryForObject("select status from competition_units where id=? for update",String.class,UNIT);
    future.add(pool.submit(()->experiment.execute(variant,UNIT,"ASC")));
    waitForBlocked("rank-unit-lock");
    jdbc.update("update competition_units set status='PUBLISHED',version=version+1 where id=?",UNIT);
   });
   ExecutionException failure=assertThrows(ExecutionException.class,()->future.getFirst().get(10,TimeUnit.SECONDS));
   RuleFailure cause=assertInstanceOf(RuleFailure.class,failure.getCause());
   assertEquals("results.unit_published",cause.getCode()); assertEquals(before,states());
   passed(variant+"-publication-commits-before-ranking-lock");
  } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
 }
 void verifyConcurrentEdit(String variant) throws Exception {
  reset(List.of(pending("50"),pending("60")),"DRAFT");
  ExecutorService pool=Executors.newSingleThreadExecutor(); List<Future<?>> future=new ArrayList<>();
  try {
   new TransactionTemplate(manager).executeWithoutResult(tx->{
    jdbc.queryForObject("select id from results where id=? for update",UUID.class,id(0));
    future.add(pool.submit(()->experiment.execute(variant,UNIT,"ASC")));
    waitForBlocked("rank-result-lock");
    jdbc.update("update results set raw_value='70',version=version+1 where id=?",id(0));
   });
   future.getFirst().get(10,TimeUnit.SECONDS);
   List<State> after=states();
   assertEquals("70",after.get(0).raw()); assertEquals(2,after.get(0).place()); assertEquals(2,after.get(0).version());
   assertEquals(1,after.get(1).place()); assertEquals(1,after.get(1).version());
   passed(variant+"-concurrent-edit-read-after-lock");
  } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
 }
 List<Seed> dataset(int n) {
  List<Seed> out=new ArrayList<>();
  for(int i=0;i<n;i++) {
   if(i%11==10) out.add(new Seed(null,"DNS",null));
   else {
    int cs=3000+((i*37)%5000);
    String raw=String.format(Locale.ROOT,"%02d:%02d.%02d",cs/6000,(cs%6000)/100,cs%100);
    out.add(pending(raw));
   }
  }
  return out;
 }
 BigDecimal decimalValue(String raw) {
  if(!raw.contains(":")) return new BigDecimal(raw);
  String[] p=raw.split(":"); return new BigDecimal(p[0]).multiply(BigDecimal.valueOf(60)).add(new BigDecimal(p[1]));
 }
 void verifyDataset(List<Seed> seeds,String strategy) {
  List<Integer> order=new ArrayList<>();
  for(int i=0;i<seeds.size();i++) if(NumericContract.rankable(seeds.get(i).status())) order.add(i);
  Comparator<Integer> c=Comparator.comparing(i->decimalValue(seeds.get(i).raw()));
  if(strategy.equals("DESC")) c=c.reversed();
  order.sort(c.thenComparing(i->id(i).toString()));
  Map<Integer,Integer> places=new HashMap<>();
  for(int i=0;i<order.size();i++) places.put(order.get(i),i+1);
  List<State> actual=states(); assertEquals(seeds.size(),actual.size());
  for(int i=0;i<seeds.size();i++) {
   Seed s=seeds.get(i); State a=actual.get(i);
   assertEquals(s.raw(),a.raw()); assertEquals(BASE,a.created());
   if(places.containsKey(i)) {
    assertEquals(places.get(i),a.place()); assertEquals("VALID",a.status()); assertEquals(1,a.version());
    assertTrue(a.updated().isAfter(BASE));
   } else {
    assertEquals(s.status(),a.status()); assertEquals(s.oldPlace(),a.place()); assertEquals(0,a.version()); assertEquals(BASE,a.updated());
   }
  }
 }
 void benchmark() throws Exception {
  int[] sizes=Arrays.stream(System.getProperty("research.sizes","8,64,256").split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
  int warmup=Integer.parseInt(System.getProperty("research.warmup","5"));
  int repetitions=Integer.parseInt(System.getProperty("research.samples","30"));
  assertTrue(repetitions>=10); assertTrue(warmup>=1); for(int n:sizes) assertTrue(n>=1 && n<=100000);
  List<Sample> samples=new ArrayList<>();
  List<String> meters=new ArrayList<>(List.of("n,strategy,variant,jdbc_executions,jdbc_batch_executions,batch_items_submitted,resultset_rows_consumed"));
  List<String> datasets=new ArrayList<>(List.of("n,result_id,raw_value,status"));
  for(int n:sizes) {
   List<Seed> seeds=dataset(n);
   for(int i=0;i<seeds.size();i++) datasets.add(n+","+id(i)+","+Objects.toString(seeds.get(i).raw(),"")+","+seeds.get(i).status());
   for(String strategy:List.of("ASC","DESC")) {
    for(String variant:List.of("JAVA","SQL")) {
     reset(seeds,"DRAFT"); JdbcMeter.Counts count=new JdbcMeter.Counts();
     JdbcMeter.ACTIVE.set(count);
     try { experiment.execute(variant,UNIT,strategy); } finally { JdbcMeter.ACTIVE.remove(); }
     verifyDataset(seeds,strategy);
     assertTrue(count.executions>=4,"JDBC meter did not capture executions");
     meters.add(n+","+strategy+","+variant+","+count.executions+","+count.batchExecutions+","+count.batchItems+","+count.rowsConsumed);
    }
    for(int i=0;i<warmup;i++) for(String variant:List.of("JAVA","SQL")) {
     reset(seeds,"DRAFT"); experiment.execute(variant,UNIT,strategy); verifyDataset(seeds,strategy);
    }
    for(int i=0;i<repetitions;i++) {
     List<String> order=i%2==0?List.of("JAVA","SQL"):List.of("SQL","JAVA");
     for(String variant:order) {
      reset(seeds,"DRAFT");
      long start=System.nanoTime(); experiment.execute(variant,UNIT,strategy); long elapsed=System.nanoTime()-start;
      verifyDataset(seeds,strategy);
      samples.add(new Sample(n,strategy,variant,i,order.getFirst(),elapsed));
     }
    }
   }
  }
  List<String> raw=new ArrayList<>(List.of("n,strategy,variant,iteration,first_variant,elapsed_ns"));
  for(Sample s:samples) raw.add(s.n()+","+s.strategy()+","+s.variant()+","+s.iteration()+","+s.order()+","+s.ns());
  Files.write(output.resolve("raw-timings.csv"),raw);
  Files.write(output.resolve("jdbc-metrics.csv"),meters);
  Files.write(output.resolve("datasets.csv"),datasets);
  List<String> summary=new ArrayList<>(List.of("n,strategy,variant,samples,median_ms,p25_ms,p75_ms,min_ms,max_ms"));
  for(int n:sizes) for(String strategy:List.of("ASC","DESC")) for(String variant:List.of("JAVA","SQL")) {
   double[] values=samples.stream().filter(s->s.n()==n && s.strategy().equals(strategy) && s.variant().equals(variant)).mapToDouble(s->s.ns()/1e6).sorted().toArray();
   summary.add(String.format(Locale.ROOT,"%d,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f",n,strategy,variant,values.length,
    quantile(values,.5),quantile(values,.25),quantile(values,.75),values[0],values[values.length-1]));
  }
  Files.write(output.resolve("summary.csv"),summary);
  Properties metadata=new Properties();
  for(String key:List.of("java.version","java.vm.name","os.name","os.version","os.arch")) metadata.setProperty(key,System.getProperty(key));
  metadata.setProperty("processors",String.valueOf(Runtime.getRuntime().availableProcessors()));
  metadata.setProperty("max_heap_bytes",String.valueOf(Runtime.getRuntime().maxMemory()));
  metadata.setProperty("postgres",jdbc.queryForObject("select version()",String.class));
  metadata.setProperty("jdbc_batch_size",System.getProperty("research.batch","0"));
  metadata.setProperty("warmup_per_variant",String.valueOf(warmup));
  metadata.setProperty("samples_per_variant",String.valueOf(repetitions));
  metadata.setProperty("sizes",Arrays.toString(sizes));
  metadata.setProperty("dataset_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",datasets).getBytes(StandardCharsets.UTF_8))));
  metadata.setProperty("measurement","external call to transactional proxy including commit; fixtures and assertions excluded; JDBC counters separate pass");
  metadata.setProperty("source_revision",System.getProperty("research.revision","not-specified"));
  try(var writer=Files.newBufferedWriter(output.resolve("environment.properties"))) { metadata.store(writer,"Research run"); }
  Files.writeString(output.resolve("status.txt"),"Functional checks and dataset assertions completed. Timings collected. Hypothesis interpretation NOT AUTOMATED.\n");
 }
 static double quantile(double[] values,double q) {
  double position=(values.length-1)*q; int lo=(int)Math.floor(position),hi=(int)Math.ceil(position);
  return values[lo]+(values[hi]-values[lo])*(position-lo);
 }
}
