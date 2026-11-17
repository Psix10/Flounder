package com.acme.sportplatform.identity.application.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final AdminBootstrapService service;
    private final Environment environment;

    public AdminBootstrapRunner(AdminBootstrapService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        var outcome = service.initialize(
                environment.getProperty("app.bootstrap.admin.email"),
                environment.getProperty("app.bootstrap.admin.password"));
        LOG.info("Bootstrap administrator initialization completed: {}", outcome);
    }
}
