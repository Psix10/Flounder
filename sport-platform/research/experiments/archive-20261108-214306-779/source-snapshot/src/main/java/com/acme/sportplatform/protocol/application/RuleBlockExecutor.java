package com.acme.sportplatform.protocol.application;

public interface RuleBlockExecutor {
    String code();
    void apply(ProtocolGenerationContext context);
}