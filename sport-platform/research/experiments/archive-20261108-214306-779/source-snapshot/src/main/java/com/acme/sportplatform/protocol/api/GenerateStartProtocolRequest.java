package com.acme.sportplatform.protocol.api;
import java.util.UUID;
public record GenerateStartProtocolRequest(Boolean regenerate, UUID ruleSetVersionId) {
    
    public GenerateStartProtocolRequest(Boolean regenerate) { 
        this(regenerate, null); 
    }
}
