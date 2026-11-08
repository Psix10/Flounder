package com.acme.sportplatform.integration.protocol;
import java.util.*;
final class SessionProtocolExperimentReport {
    static final long LIMIT_NANOS=5_000_000_000L;
    record Measurement(String scenario,int iteration,long elapsedNanos,boolean correct,String error) {
        Measurement { if(elapsedNanos<0) throw new IllegalArgumentException("Negative duration"); }
    }
    record Summary(String scenario,int runs,int correctRuns,int correctWithin5s,double qualifiedShare,
                   double p50Ms,double p95Ms,double maxMs,String assessment) { }
    static Summary summarize(String scenario,List<Measurement> measurements) {
        var rows=measurements.stream().filter(m->m.scenario().equals(scenario)).toList();
        if(rows.isEmpty()) throw new IllegalArgumentException("No measurements for "+scenario);
        long[] sorted=rows.stream().mapToLong(Measurement::elapsedNanos).sorted().toArray();
        int correct=(int)rows.stream().filter(Measurement::correct).count();
        int qualified=(int)rows.stream().filter(m->m.correct() && m.elapsedNanos()<=LIMIT_NANOS).count();
        double share=qualified/(double)rows.size();
        String assessment=rows.size()<30?"EXPLORATORY":share>=0.95?"MET":"NOT_MET";
        return new Summary(scenario,rows.size(),correct,qualified,share,
                percentile(sorted,0.5)/1_000_000.0,percentile(sorted,0.95)/1_000_000.0,
                sorted[sorted.length-1]/1_000_000.0,assessment);
    }
    private static long percentile(long[] sorted,double fraction) {
        return sorted[Math.max(0,(int)Math.ceil(fraction*sorted.length)-1)];
    }
    static String csv(Object value) {
        return "\""+String.valueOf(value).replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";
    }
}
