package com.acme.sportplatform.identity.application.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminBootstrapRunnerTest {
    private final AdminBootstrapService service = mock(AdminBootstrapService.class);
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withBean(AdminBootstrapService.class, () -> service)
                .withUserConfiguration(AdminBootstrapRunner.class);
    }
    @Test void disabledByDefault() {
        context().run(ctx -> {
            assertTrue(ctx.getBeansOfType(AdminBootstrapRunner.class).isEmpty());
            verifyNoInteractions(service);
        });
    }
    @Test void explicitlyDisabledDoesNotRun() {
        context().withPropertyValues("app.bootstrap.admin.enabled=false").run(ctx -> {
            assertTrue(ctx.getBeansOfType(AdminBootstrapRunner.class).isEmpty());
            verifyNoInteractions(service);
        });
    }
    @Test void enabledRunnerReadsSettingsAndCallsTransactionalService() {
        context().withPropertyValues("app.bootstrap.admin.enabled=true",
                "app.bootstrap.admin.email=admin@example.invalid",
                "app.bootstrap.admin.password=Strong-Demo-Password-2026!").run(ctx -> {
            var runner = ctx.getBean(AdminBootstrapRunner.class);
            runner.run(new DefaultApplicationArguments(new String[0]));
            verify(service).initialize("admin@example.invalid", "Strong-Demo-Password-2026!");
        });
    }
}
