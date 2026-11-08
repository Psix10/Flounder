package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class RuleExecutorRegistry {

    private final Map<String, RuleBlockExecutor> executorsByCode;

    public RuleExecutorRegistry(List<RuleBlockExecutor> executors) {
        this.executorsByCode = executors.stream()
                .collect(Collectors.toMap(
                        RuleBlockExecutor::code,
                        Function.identity()
                ));
    }

    public RuleBlockExecutor get(String code) {
        RuleBlockExecutor executor = executorsByCode.get(code);

        if (executor == null) {
            throw new IllegalArgumentException("Rule executor not found: " + code);
        }

        return executor;
    }
}