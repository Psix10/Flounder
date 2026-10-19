package com.acme.research.ranking;
import java.util.regex.Pattern;
public final class NumericContract {
 private NumericContract() {}
 private static final Pattern DECIMAL=Pattern.compile("[+-]?[0-9]+(?:[.][0-9]+)?");
 private static final Pattern TIME=Pattern.compile("[0-9]+:[0-9]{1,2}(?:[.][0-9]+)?");
 public static Double parse(String raw) {
  if (raw==null || raw.length()>50) return null;
  if (DECIMAL.matcher(raw).matches()) {
   double value=Double.parseDouble(raw);
   return Double.isFinite(value) ? (value == 0d ? 0d : value) : null;
  }
  if (!TIME.matcher(raw).matches()) return null;
  String[] parts=raw.split(":",-1);
  double minutes=Double.parseDouble(parts[0]);
  double seconds=Double.parseDouble(parts[1]);
  double value=minutes*60+seconds;
  return seconds<60 && Double.isFinite(value) ? value : null;
 }
 public static boolean rankable(String status) {
  return "PENDING".equals(status) || "VALID".equals(status);
 }
 public static boolean descending(String strategy) {
  if ("ASC".equalsIgnoreCase(strategy)) return false;
  if ("DESC".equalsIgnoreCase(strategy)) return true;
  throw new RuleFailure("results.invalid_ranking_strategy");
 }
}
