package com.acme.sportplatform.integration.registrations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.organizations.infrastructure.jpa.*;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.application.ApplyProviderPaymentResultUseCase;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class RegistrationWithdrawalIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private OrganizationRepository organizations;
    @Autowired private OrganizationMemberRepository members;
    @Autowired private EventRepository events;
    @Autowired private DisciplineTemplateRepository templates;
    @Autowired private EventDisciplineRepository disciplines;
    @Autowired private RegistrationRepository registrations;
    @Autowired private PaymentRepository payments;
    @Autowired private CompetitionUnitRepository units;
    @Autowired private ApplyProviderPaymentResultUseCase applyProviderResult;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void withdrawsAndCancelsUnpaidManualPaymentWithoutChangingSnapshot() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        var before = registrations.findById(f.registrationId()).orElseThrow();
        String snapshot = before.getParticipantSnapshot();
        String request = reason();
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        var registration = registrations.findById(f.registrationId()).orElseThrow();
        var payment = payments.findById(f.paymentId()).orElseThrow();
        assertEquals("WITHDRAWN", registration.getStatus());
        assertEquals(snapshot, registration.getParticipantSnapshot());
        var meta = json.readTree(registration.getRegistrationMeta());
        assertEquals(64.44, meta.path("seedTimeSeconds").asDouble());
        assertEquals("preserved", meta.path("keep").asText());
        assertEquals("WITHDRAW_CONFIRMED_REGISTRATION",
                meta.path("withdrawalHistory").get(0).path("operation").asText());
        assertEquals(f.organizer().getUserId().toString(),
                meta.path("withdrawalHistory").get(0).path("actorUserId").asText());
        assertEquals("CANCELED", payment.getStatus());
        assertNotNull(payment.getCanceledAt());
        assertNull(payment.getPaidAt());
        var paymentMeta = json.readTree(payment.getProviderMetadata());
        assertEquals("preserved", paymentMeta.path("keep").asText());
        assertEquals(f.registrationId().toString(),
                paymentMeta.path("registrationWithdrawal").path("registrationId").asText());
    }

    @Test
    void withdrawsWithoutPayment() throws Exception {
        var f = fixture(null, null);
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertTrue(payments.findByRegistrationId(f.registrationId()).isEmpty());
    }

    @Test
    void rejectsForeignOrganizerWithoutChangingPayment() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        var stranger = principal(createUser(), "ORGANIZER");
        mvc.perform(post(route(f)).with(user(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isForbidden());
        assertUnchanged(f, "CREATED");
    }

    @Test
    void rejectsParticipant() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        mvc.perform(post(route(f)).with(user(f.participant()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isForbidden());
        assertUnchanged(f, "CREATED");
    }

    @Test
    void rejectsPaidPayment() throws Exception {
        var f = fixture("MANUAL", "SUCCEEDED");
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isConflict());
        assertUnchanged(f, "SUCCEEDED");
    }

    @Test
    void rejectsPendingAndExternalPayments() throws Exception {
        for (var example : List.of(List.of("MANUAL", "PENDING"), List.of("YOOKASSA", "CREATED"))) {
            var f = fixture(example.get(0), example.get(1));
            mvc.perform(post(route(f)).with(user(f.organizer()))
                            .contentType(MediaType.APPLICATION_JSON).content(reason()))
                    .andExpect(status().isConflict());
            assertUnchanged(f, example.get(1));
        }
    }

    @Test
    void rejectsExistingCompetitionUnitBeforeCancelingPayment() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var unit = new CompetitionUnitEntity();
        unit.setId(UUID.randomUUID());
        unit.setEventDisciplineId(f.disciplineId());
        unit.setLabel("Existing heat");
        unit.setSequenceNumber(1);
        unit.setStatus("DRAFT");
        unit.setCreatedAt(now);
        unit.setUpdatedAt(now);
        units.saveAndFlush(unit);
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isConflict());
        assertUnchanged(f, "CREATED");
    }

    @Test
    void rejectsBlankReason() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertUnchanged(f, "CREATED");
    }

    @Test
    void canceledPaymentCannotBeConfirmedOrOverwrittenByInitialProviderResult() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/payments/{id}/confirm", f.paymentId())
                        .with(user(principal(createUser(), "OPERATOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payments.not_confirmable"));
        assertThrows(BusinessException.class, () -> applyProviderResult.execute(
                f.paymentId(), mock(ProviderPaymentResult.class)
        ));
        assertEquals("CANCELED", payments.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals("WITHDRAWN", registrations.findById(f.registrationId()).orElseThrow().getStatus());
    }

    @Test
    void repeatedWithdrawalDoesNotDuplicateAudit() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isOk());
        String meta = registrations.findById(f.registrationId()).orElseThrow().getRegistrationMeta();
        mvc.perform(post(route(f)).with(user(f.organizer()))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isConflict());
        assertEquals(json.readTree(meta), json.readTree(
                registrations.findById(f.registrationId()).orElseThrow().getRegistrationMeta()));
    }

    @Test
    void concurrentConfirmationAndWithdrawalHaveOnlyOneWinningTransition() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        var operator = principal(createUser(), "OPERATOR");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var withdrawal = pool.submit(() -> {
                start.await();
                return mvc.perform(post(route(f)).with(user(f.organizer()))
                                .contentType(MediaType.APPLICATION_JSON).content(reason()))
                        .andReturn().getResponse().getStatus();
            });
            var confirmation = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/payments/{id}/confirm", f.paymentId())
                                .with(user(operator)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            int withdrawnStatus = withdrawal.get(30, TimeUnit.SECONDS);
            int confirmedStatus = confirmation.get(30, TimeUnit.SECONDS);
            var registration = registrations.findById(f.registrationId()).orElseThrow();
            var payment = payments.findById(f.paymentId()).orElseThrow();
            if (withdrawnStatus == 200) {
                assertEquals(400, confirmedStatus);
                assertEquals("WITHDRAWN", registration.getStatus());
                assertEquals("CANCELED", payment.getStatus());
                assertNull(payment.getPaidAt());
            } else {
                assertEquals(409, withdrawnStatus);
                assertEquals(200, confirmedStatus);
                assertEquals("CONFIRMED", registration.getStatus());
                assertEquals("SUCCEEDED", payment.getStatus());
                assertNull(payment.getCanceledAt());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void platformAdminCanWithdrawWithoutOrganizationMembership() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        var admin = principal(createUser(), "PLATFORM_ADMIN");
        mvc.perform(post(route(f)).with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(reason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertEquals("CANCELED", payments.findById(f.paymentId()).orElseThrow().getStatus());
    }

    @Test
    void registrationWriteFailureRollsBackPaymentCancellationAndBothAudits() throws Exception {
        var f = fixture("MANUAL", "CREATED");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "test_withdrawal_failure_" + suffix;
        String trigger = "test_withdrawal_trigger_" + suffix;
        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.id = '%s'::uuid AND NEW.status = 'WITHDRAWN' THEN
                        RAISE EXCEPTION 'injected withdrawal test failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(function, f.registrationId()));
        try {
            jdbc.execute("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON registrations FOR EACH ROW EXECUTE FUNCTION "
                    + function + "()");
            boolean failed;
            try {
                int status = mvc.perform(post(route(f)).with(user(f.organizer()))
                                .contentType(MediaType.APPLICATION_JSON).content(reason()))
                        .andReturn().getResponse().getStatus();
                failed = status >= 500;
            } catch (jakarta.servlet.ServletException exception) {
                failed = true;
            }
            assertTrue(failed, "Injected database failure must not return success");
            assertUnchanged(f, "CREATED");
            var payment = payments.findById(f.paymentId()).orElseThrow();
            assertFalse(json.readTree(payment.getProviderMetadata()).has("registrationWithdrawal"));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON registrations");
            jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }

    private void assertUnchanged(Fixture f, String paymentStatus) throws Exception {
        var r = registrations.findById(f.registrationId()).orElseThrow();
        assertEquals("CONFIRMED", r.getStatus());
        assertFalse(json.readTree(r.getRegistrationMeta()).has("withdrawalHistory"));
        var p = payments.findById(f.paymentId()).orElseThrow();
        assertEquals(paymentStatus, p.getStatus());
        assertNull(p.getCanceledAt());
    }

    private String route(Fixture f) {
        return "/api/v1/registrations/" + f.registrationId() + "/withdraw-confirmed";
    }
    private String reason() {
        return "{\"reason\":\"Test fixture does not participate\"}";
    }
    private UserEntity createUser() {
        var u = new UserEntity();
        u.setEmail("withdrawal-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("test-password-hash");
        u.setStatus("active");
        return users.saveAndFlush(u);
    }
    private PlatformUserPrincipal principal(UserEntity u, String role) {
        return new PlatformUserPrincipal(u.getId(), u.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private Fixture fixture(String provider, String paymentStatus) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var organizer = createUser();
        var participant = createUser();
        var profile = new ProfileEntity();
        profile.setUserId(participant.getId());
        profile.setFirstName("Withdrawal");
        profile.setLastName("Fixture");
        profile.setBirthDate(LocalDate.parse("2000-01-01"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Moscow");
        profile.setClubName("Withdrawal test club");
        profile.setSportMeta(Map.of());
        profile = profiles.saveAndFlush(profile);

        var organization = new OrganizationEntity();
        organization.setType("CLUB");
        organization.setName("Withdrawal fixture " + UUID.randomUUID());
        organization.setMeta("{}");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);
        organization = organizations.saveAndFlush(organization);
        var member = new OrganizationMemberEntity();
        member.setOrganizationId(organization.getId());
        member.setUserId(organizer.getId());
        member.setMemberRole("OWNER");
        member.setCreatedAt(now);
        members.saveAndFlush(member);

        var event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(organizer.getId());
        event.setOrganizationId(organization.getId());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SPORT);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Withdrawal integration fixture");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(now.plusDays(20));
        event.setEventEndAt(now.plusDays(21));
        event.setStatus("REGISTRATION_OPEN");
        event.setPublicSlug("withdrawal-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        events.saveAndFlush(event);

        var template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SPORT);
        template.setCode("withdrawal-" + UUID.randomUUID());
        template.setName("Withdrawal fixture discipline");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("{}");
        templates.saveAndFlush(template);
        var discipline = new EventDisciplineEntity();
        discipline.setId(UUID.randomUUID());
        discipline.setEventId(event.getId());
        discipline.setDisciplineTemplateId(template.getId());
        discipline.setCode("withdrawal-" + UUID.randomUUID());
        discipline.setName("Withdrawal fixture discipline");
        discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT");
        discipline.setResultType("TIME");
        discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(40);
        discipline.setEntryFeeAmount(new BigDecimal("500.00"));
        discipline.setEntryFeeCurrency("RUB");
        discipline.setStatus("PUBLISHED");
        discipline.setSettingsJson("{}");
        discipline.setCreatedAt(now);
        discipline.setUpdatedAt(now);
        disciplines.saveAndFlush(discipline);

        var r = new RegistrationEntity();
        r.setId(UUID.randomUUID());
        r.setEventId(event.getId());
        r.setEventDisciplineId(discipline.getId());
        r.setParticipantUserId(participant.getId());
        r.setParticipantProfileId(profile.getId());
        r.setStatus("CONFIRMED");
        r.setParticipantSnapshot("{\"gender\":\"legacy-invalid\"}");
        r.setRegistrationMeta("{\"seedTimeSeconds\":64.44,\"keep\":\"preserved\"}");
        r.setSubmittedAt(now);
        r.setReviewedByUserId(organizer.getId());
        r.setReviewedAt(now);
        r.setReviewNote("Previously confirmed");
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        registrations.saveAndFlush(r);

        UUID paymentId = null;
        if (provider != null) {
            var p = new PaymentEntity();
            p.setId(UUID.randomUUID());
            p.setRegistrationId(r.getId());
            p.setAmount(new BigDecimal("500.00"));
            p.setCurrency("RUB");
            p.setStatus(paymentStatus);
            p.setProvider(provider);
            p.setIdempotencyKey(UUID.randomUUID());
            p.setProviderMetadata("{\"keep\":\"preserved\"}");
            if ("SUCCEEDED".equals(paymentStatus)) p.setPaidAt(now);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            paymentId = payments.saveAndFlush(p).getId();
        }
        return new Fixture(r.getId(), discipline.getId(), paymentId,
                principal(organizer, "ORGANIZER"), principal(participant, "PARTICIPANT"));
    }

    private record Fixture(UUID registrationId, UUID disciplineId, UUID paymentId,
                           PlatformUserPrincipal organizer, PlatformUserPrincipal participant) {
    }
}
