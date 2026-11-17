package com.acme.sportplatform.integration.payments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
import com.acme.sportplatform.payments.domain.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.payments.provider=MANUAL")
class ManualPaymentIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SWIMMING_SPORT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DisciplineTemplateRepository disciplineTemplateRepository;

    @Autowired
    private EventDisciplineRepository eventDisciplineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Test
    void shouldCreateAndConfirmManualPaymentAsOperator() throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();
        UUID paymentId = createManualPayment(fixture);

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                paymentId
                        )
                                .with(user(principal("OPERATOR")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.provider").value("MANUAL"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(
                PaymentStatus.SUCCEEDED.name(),
                payment.getStatus()
        );
        assertNotNull(payment.getPaidAt());
    }

    @Test
    void shouldForbidOrganizerAndParticipantFromConfirmingPayment()
            throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();
        UUID paymentId = createManualPayment(fixture);

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                paymentId
                        )
                                .with(user(principal("ORGANIZER")))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                paymentId
                        )
                                .with(user(fixture.participantPrincipal()))
                )
                .andExpect(status().isForbidden());

        assertEquals(
                PaymentStatus.CREATED.name(),
                paymentRepository.findById(paymentId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectRepeatedManualConfirmation() throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();
        UUID paymentId = createManualPayment(fixture);

        PlatformUserPrincipal operator = principal("OPERATOR");

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                paymentId
                        )
                                .with(user(operator))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                paymentId
                        )
                                .with(user(operator))
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("payments.not_confirmable")
                );
    }

    @Test
    void shouldRejectManualConfirmationForYooKassaPayment()
            throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();

        PaymentEntity payment = savePayment(
                fixture.registrationId(),
                PaymentProvider.YOOKASSA,
                PaymentStatus.PENDING
        );

        mockMvc.perform(
                        post(
                                "/api/v1/payments/{paymentId}/confirm",
                                payment.getId()
                        )
                                .with(user(principal("OPERATOR")))
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "payments.manual_confirmation_not_allowed"
                                )
                );

        assertEquals(
                PaymentStatus.PENDING.name(),
                paymentRepository.findById(payment.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectDuplicatePaymentCreation() throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();

        createManualPayment(fixture);

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/payments",
                                fixture.registrationId()
                        )
                                .with(user(fixture.participantPrincipal()))
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("payments.already_exists")
                );
    }

    @Test
    void shouldAllowOperatorToReadPaymentForReview() throws Exception {
        RegistrationFixture fixture = createConfirmedPaidRegistration();
        UUID paymentId = createManualPayment(fixture);

        mockMvc.perform(
                        get(
                                "/api/v1/payments/review/registrations/{registrationId}",
                                fixture.registrationId()
                        )
                                .with(user(principal("OPERATOR")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.provider").value("MANUAL"));
    }

    private UUID createManualPayment(
            RegistrationFixture fixture
    ) throws Exception {
        String response = mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/payments",
                                fixture.registrationId()
                        )
                                .with(user(fixture.participantPrincipal()))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.registrationId")
                                .value(fixture.registrationId().toString())
                )
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.provider").value("MANUAL"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = JsonPath.read(response, "$.id");

        return UUID.fromString(paymentId);
    }

    private RegistrationFixture createConfirmedPaidRegistration()
            throws Exception {
        UserEntity participant = createParticipantUser();
        createParticipantProfile(participant.getId());

        UserEntity organizer = createOrganizerUser();

        UUID organizationId =
                createOrganizationWithOwner(organizer.getId());

        PlatformUserPrincipal organizerPrincipal =
                new PlatformUserPrincipal(
                        organizer.getId(),
                        organizer.getEmail(),
                        "",
                        "active",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ORGANIZER")
                        )
                );

        UUID eventId = createEventWithOpenRegistration(
                organizer.getId(),
                organizationId
        );

        UUID disciplineTemplateId = createDisciplineTemplate();

        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId,
                new BigDecimal("1500.00")
        );

        PlatformUserPrincipal participantPrincipal =
                new PlatformUserPrincipal(
                        participant.getId(),
                        participant.getEmail(),
                        "",
                        "active",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_PARTICIPANT")
                        )
                );

        String registrationRequestJson = """
                {
                  "eventDisciplineId": "%s",
                  "registrationMeta": {
                    "emergencyContactPhone": "+79990000000",
                    "comment": "Manual payment integration fixture"
                  }
                }
                """.formatted(eventDisciplineId);

        String registrationResponse = mockMvc.perform(
                        post("/api/v1/registrations")
                                .with(user(participantPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequestJson)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationIdValue =
                JsonPath.read(registrationResponse, "$.id");

        UUID registrationId = UUID.fromString(registrationIdValue);

        String reviewRequestJson = """
                {
                  "decision": "CONFIRMED",
                  "reviewNote": "Manual payment registration confirmed"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/review",
                                registrationId
                        )
                                .with(user(organizerPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewRequestJson)
                )
                .andExpect(status().isOk());

        return new RegistrationFixture(
                registrationId,
                participantPrincipal
        );
    }

    private PaymentEntity savePayment(
            UUID registrationId,
            PaymentProvider provider,
            PaymentStatus status
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentEntity payment = new PaymentEntity();

        payment.setId(UUID.randomUUID());
        payment.setRegistrationId(registrationId);
        payment.setAmount(new BigDecimal("1500.00"));
        payment.setCurrency("RUB");
        payment.setStatus(status.name());
        payment.setProvider(provider.name());
        payment.setExternalPaymentId(
                provider == PaymentProvider.YOOKASSA
                        ? "test-yookassa-" + UUID.randomUUID()
                        : null
        );
        payment.setIdempotencyKey(UUID.randomUUID());
        payment.setProviderMetadata("{}");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        return paymentRepository.saveAndFlush(payment);
    }

    private PlatformUserPrincipal principal(String role) {
        return new PlatformUserPrincipal(
                UUID.randomUUID(),
                role.toLowerCase()
                        + "-"
                        + UUID.randomUUID()
                        + "@example.com",
                "",
                "active",
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
    }

    private UserEntity createParticipantUser() {
        UserEntity participant = new UserEntity();

        participant.setEmail(
                "manual-payment-participant-"
                        + UUID.randomUUID()
                        + "@example.com"
        );
        participant.setPhone("+79990000000");
        participant.setPasswordHash("test-password-hash");
        participant.setStatus("active");

        return userRepository.saveAndFlush(participant);
    }

    private UserEntity createOrganizerUser() {
        UserEntity organizer = new UserEntity();

        organizer.setEmail(
                "manual-payment-organizer-"
                        + UUID.randomUUID()
                        + "@example.com"
        );
        organizer.setPhone("+79990000001");
        organizer.setPasswordHash("test-password-hash");
        organizer.setStatus("active");

        return userRepository.saveAndFlush(organizer);
    }

    private UUID createOrganizationWithOwner(UUID ownerUserId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OrganizationEntity organization = new OrganizationEntity();

        organization.setType("CLUB");
        organization.setName(
                "Manual payment test organization " + UUID.randomUUID()
        );
        organization.setMeta("{}");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);

        OrganizationEntity saved =
                organizationRepository.saveAndFlush(organization);

        OrganizationMemberEntity owner =
                new OrganizationMemberEntity();

        owner.setOrganizationId(saved.getId());
        owner.setUserId(ownerUserId);
        owner.setMemberRole("OWNER");
        owner.setCreatedAt(now);

        organizationMemberRepository.saveAndFlush(owner);

        return saved.getId();
    }

    private void createParticipantProfile(UUID userId) {
        ProfileEntity profile = new ProfileEntity();

        profile.setUserId(userId);
        profile.setFirstName("Manual");
        profile.setLastName("Participant");
        profile.setMiddleName("Test");
        profile.setBirthDate(LocalDate.parse("2000-01-01"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Санкт-Петербург");
        profile.setClubName("Manual payment test club");
        profile.setSportMeta(
                Map.of("sportRank", "FIRST_CATEGORY")
        );

        profileRepository.saveAndFlush(profile);
    }

    private UUID createEventWithOpenRegistration(
            UUID createdByUserId,
            UUID organizationId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();

        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(createdByUserId);
        event.setOrganizationId(organizationId);
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Manual payment integration event");
        event.setDescription("Manual payment integration fixture");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(EventStatus.REGISTRATION_OPEN.name());
        event.setPublicSlug("manual-payment-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.saveAndFlush(event).getId();
    }

    private UUID createDisciplineTemplate() {
        DisciplineTemplateEntity template =
                new DisciplineTemplateEntity();

        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING_SPORT_ID);
        template.setCode(
                "manual-payment-discipline-" + UUID.randomUUID()
        );
        template.setName("Manual payment test discipline");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("""
                {
                  "distanceMeters": 100,
                  "stroke": "FREESTYLE",
                  "poolLengthMeters": 25
                }
                """);

        return disciplineTemplateRepository
                .saveAndFlush(template)
                .getId();
    }

    private UUID createPublishedEventDiscipline(
            UUID eventId,
            UUID disciplineTemplateId,
            BigDecimal entryFeeAmount
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventDisciplineEntity discipline =
                new EventDisciplineEntity();

        discipline.setId(UUID.randomUUID());
        discipline.setEventId(eventId);
        discipline.setDisciplineTemplateId(disciplineTemplateId);
        discipline.setCode(
                "manual-payment-100m-" + UUID.randomUUID()
        );
        discipline.setName("Manual payment paid discipline");
        discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT");
        discipline.setResultType("TIME");
        discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(100);
        discipline.setEntryFeeAmount(entryFeeAmount);
        discipline.setEntryFeeCurrency("RUB");
        discipline.setStatus(EventDisciplineStatus.PUBLISHED.name());
        discipline.setSettingsJson("""
                {
                  "distanceMeters": 100,
                  "stroke": "FREESTYLE",
                  "gender": "MALE"
                }
                """);
        discipline.setCreatedAt(now);
        discipline.setUpdatedAt(now);

        return eventDisciplineRepository
                .saveAndFlush(discipline)
                .getId();
    }

    private record RegistrationFixture(
            UUID registrationId,
            PlatformUserPrincipal participantPrincipal
    ) {
    }
}