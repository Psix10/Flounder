package com.acme.sportplatform.protocol.application.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.acme.sportplatform.protocol.application.ProtocolGenerationContext;
import com.acme.sportplatform.protocol.application.SeedParticipant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class SeedByMetricAscNullsLastExecutorTest {

    private final SeedByMetricAscNullsLastExecutor executor =
            new SeedByMetricAscNullsLastExecutor();

    private SeedParticipant participant(int id, String metric) {
        UUID registrationId = UUID.fromString(
                String.format(
                        java.util.Locale.ROOT,
                        "00000000-0000-0000-0000-%012d",
                        id
                )
        );

        return new SeedParticipant(
                registrationId,
                metric == null ? null : new BigDecimal(metric)
        );
    }

    private List<SeedParticipant> sorted(
            List<SeedParticipant> input
    ) {
        ProtocolGenerationContext context =
                mock(ProtocolGenerationContext.class);

        when(context.getParticipants()).thenReturn(input);

        List<SeedParticipant> result = new ArrayList<>();

        doAnswer(invocation -> {
            List<SeedParticipant> assigned =
                    invocation.getArgument(0);

            result.addAll(assigned);
            return null;
        }).when(context).setParticipants(anyList());

        executor.apply(context);

        verify(context, times(1)).setParticipants(anyList());

        return result;
    }

    @Test
    void exposesExpectedExecutorCode() {
        assertEquals(
                "SEED_BY_METRIC_ASC_NULLS_LAST",
                executor.code()
        );
    }

    @Test
    void sortsNumericMetricsAscending() {
        var slow = participant(1, "70");
        var fast = participant(2, "60");
        var middle = participant(3, "65");

        assertEquals(
                List.of(fast, middle, slow),
                sorted(List.of(slow, fast, middle))
        );
    }

    @Test
    void placesMissingMetricsAfterNumericMetrics() {
        var missing = participant(1, null);
        var slow = participant(2, "70");
        var fast = participant(3, "60");

        assertEquals(
                List.of(fast, slow, missing),
                sorted(List.of(missing, slow, fast))
        );
    }

    @Test
    void resolvesEqualNumericMetricsByRegistrationId() {
        var largerId = participant(20, "60");
        var smallerId = participant(10, "60");

        assertEquals(
                List.of(smallerId, largerId),
                sorted(List.of(largerId, smallerId))
        );
    }

    @Test
    void treatsDifferentDecimalScalesAsEqualNumericValues() {
        var largerId = participant(20, "60.0");
        var smallerId = participant(10, "60.00");

        assertEquals(
                List.of(smallerId, largerId),
                sorted(List.of(largerId, smallerId))
        );
    }

    @Test
    void resolvesMissingMetricTiesByRegistrationId() {
        var largerId = participant(20, null);
        var smallerId = participant(10, null);

        assertEquals(
                List.of(smallerId, largerId),
                sorted(List.of(largerId, smallerId))
        );
    }

    @Test
    void doesNotTreatMissingMetricAsZero() {
        var missing = participant(1, null);
        var zero = participant(2, "0");
        var positive = participant(3, "60");

        assertEquals(
                List.of(zero, positive, missing),
                sorted(List.of(missing, positive, zero))
        );
    }

    @Test
    void producesSameOrderForDifferentInputOrders() {
        var fast = participant(1, "60");
        var equalFirst = participant(2, "65");
        var equalSecond = participant(3, "65");
        var missing = participant(4, null);

        List<SeedParticipant> expected =
                List.of(fast, equalFirst, equalSecond, missing);

        assertEquals(
                expected,
                sorted(List.of(
                        missing,
                        equalSecond,
                        fast,
                        equalFirst
                ))
        );

        assertEquals(
                expected,
                sorted(List.of(
                        equalFirst,
                        fast,
                        missing,
                        equalSecond
                ))
        );
    }

    @Test
    void handlesEmptyInput() {
        assertTrue(sorted(List.of()).isEmpty());
    }

    @Test
    void doesNotModifyOriginalInputList() {
        var slow = participant(1, "70");
        var fast = participant(2, "60");

        List<SeedParticipant> input =
                new ArrayList<>(List.of(slow, fast));

        List<SeedParticipant> before =
                new ArrayList<>(input);

        assertEquals(List.of(fast, slow), sorted(input));
        assertEquals(before, input);
    }
}