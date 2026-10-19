package com.acme.research.ranking;
public class RuleFailure extends RuntimeException {
 private final String code;
 public RuleFailure(String code) { super(code); this.code=code; }
 public String getCode() { return code; }
}
