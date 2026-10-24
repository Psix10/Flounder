package com.acme.sportplatform.integration.protocol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class StartProtocolIntegrationTest extends AbstractPostgresIntegrationTest {

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

    @Test
    void shouldGenerateAndReturnStartProtocol() throws Exception {
        UserEntity maria = createParticipantUser("maria.protocol");
        createParticipantProfile(maria.getId(), "Maria", "Ivanova");

        UserEntity anna = createParticipantUser("anna.protocol");
        createParticipantProfile(anna.getId(), "Anna", "Petrova");

        UUID eventId = createEventWithOpenRegistration();
        UUID disciplineTemplateId = createDisciplineTemplate();
        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId
        );

        PlatformUserPrincipal mariaPrincipal = participantPrincipal(maria);
        PlatformUserPrincipal annaPrincipal = participantPrincipal(anna);
        PlatformUserPrincipal organizerPrincipal = organizerPrincipal();

        String mariaRegistrationId = createRegistration(
                eventDisciplineId,
                mariaPrincipal,
                28.11
        );

        String annaRegistrationId = createRegistration(
                eventDisciplineId,
                annaPrincipal,
                27.54
        );

        confirmRegistration(mariaRegistrationId, organizerPrincipal);
        confirmRegistration(annaRegistrationId, organizerPrincipal);

        mockMvc.perform(post(
                        "/api/v1/event-disciplines/{eventDisciplineId}/start-protocol/generate",
                        eventDisciplineId
                )
                        .with(user(organizerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regenerate": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(eventDisciplineId.toString()))
                .andExpect(jsonPath("$.unitCount").value(1))
                .andExpect(jsonPath("$.assignmentCount").value(2))
                .andExpect(jsonPath("$.units.length()").value(1))
                .andExpect(jsonPath("$.units[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.units[0].assignments.length()").value(2));

        mockMvc.perform(get(
                        "/api/v1/event-disciplines/{eventDisciplineId}/start-protocol",
                        eventDisciplineId
                )
                        .with(user(organizerPrincipal))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(eventDisciplineId.toString()))
                .andExpect(jsonPath("$.unitCount").value(1))
                .andExpect(jsonPath("$.assignmentCount").value(2))
                .andExpect(jsonPath("$.units.length()").value(1))
                .andExpect(jsonPath("$.units[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.units[0].assignments.length()").value(2));
    }

    @Test
    void shouldReturnBadRequestWhenStartProtocolHasNotBeenGenerated()
            throws Exception {

        UUID eventId = createEventWithOpenRegistration();
        UUID disciplineTemplateId = createDisciplineTemplate();
        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId
        );

        mockMvc.perform(get(
                        "/api/v1/event-disciplines/{eventDisciplineId}/start-protocol",
                        eventDisciplineId
                )
                        .with(user(organizerPrincipal()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("protocol.start_protocol_not_found"));
    }

    private String createRegistration(
            UUID eventDisciplineId,
            PlatformUserPrincipal participantPrincipal,
            double seedTimeSeconds
    ) throws Exception {

        String response = mockMvc.perform(post("/api/v1/registrations")
                        .with(user(participantPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventDisciplineId": "%s",
                                  "registrationMeta": {
                                    "seedTimeSeconds": %s,
                                    "emergencyContactPhone": "+79990000000"
                                  }
                                }
                                """.formatted(
                                        eventDisciplineId,
                                        seedTimeSeconds
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(eventDisciplineId.toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private void confirmRegistration(
            String registrationId,
            PlatformUserPrincipal organizerPrincipal
    ) throws Exception {

        mockMvc.perform(post("/api/v1/registrations/{registrationId}/review",
                        registrationId)
                        .with(user(organizerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "CONFIRMED",
                                  "reviewNote": "Accepted for start protocol"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registrationId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private UserEntity createParticipantUser(String emailPrefix) {
        UserEntity user = new UserEntity();
        user.setEmail(emailPrefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPhone("+7999" + randomDigits(7));
        user.setPasswordHash("test-password-hash");
        user.setStatus("active");

        return userRepository.save(user);
    }

    private void createParticipantProfile(
            UUID userId,
            String firstName,
            String lastName
    ) {
        ProfileEntity profile = new ProfileEntity();
        profile.setUserId(userId);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setMiddleName(null);
        profile.setBirthDate(LocalDate.parse("2004-05-10"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Moscow");
        profile.setClubName("Aqua Team");
        profile.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY"));

        profileRepository.save(profile);
    }

    private UUID createEventWithOpenRegistration() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(UUID.randomUUID());
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Start Protocol IT Event");
        event.setDescription("Fixture for start protocol generation");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(EventStatus.REGISTRATION_OPEN.name());
        event.setPublicSlug("start-protocol-it-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.save(event).getId();
    }

    private UUID createDisciplineTemplate() {
        DisciplineTemplateEntity template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING_SPORT_ID);
        template.setCode("50m-freestyle-" + UUID.randomUUID());
        template.setName("50m Freestyle");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("""
                {
                  "distanceMeters": 50,
                  "stroke": "FREESTYLE",
                  "poolLengthMeters": 25
                }
                """);

        return disciplineTemplateRepository.save(template).getId();
    }

    private UUID createPublishedEventDiscipline(
            UUID eventId,
            UUID disciplineTemplateId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventDisciplineEntity discipline = new EventDisciplineEntity();
        discipline.setId(UUID.randomUUID());
        discipline.setEventId(eventId);
        discipline.setDisciplineTemplateId(disciplineTemplateId);
        discipline.setCode("50m-freestyle-men");
        discipline.setName("50m Freestyle");
        discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT");
        discipline.setResultType("TIME");
        discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(40);
        discipline.setEntryFeeAmount(BigDecimal.ZERO);
        discipline.setEntryFeeCurrency("RUB");
        discipline.setStatus(EventDisciplineStatus.PUBLISHED.name());
        discipline.setSettingsJson("""
                {
                  "distanceMeters": 50,
                  "stroke": "FREESTYLE",
                  "gender": "MALE",
                  "protocol": {
                    "unitCapacity": 2,
                    "unitLabelPrefix": "Heat",
                    "seedMetricKey": "seedTimeSeconds",
                    "allowRegenerate": false,
                    "positionOrder": [2, 1]
                  }
                }
                """);
        discipline.setCreatedAt(now);
        discipline.setUpdatedAt(now);

        return eventDisciplineRepository.save(discipline).getId();
    }

    private PlatformUserPrincipal participantPrincipal(UserEntity user) {
        return new PlatformUserPrincipal(
                user.getId(),
                user.getEmail(),
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))
        );
    }

    private PlatformUserPrincipal organizerPrincipal() {
        return new PlatformUserPrincipal(
                UUID.randomUUID(),
                "organizer@example.com",
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
        );
    }

    private String randomDigits(int count) {
        String source = UUID.randomUUID()
                .toString()
                .replace("-", "");

        StringBuilder digits = new StringBuilder();

        for (int i = 0; i < source.length() && digits.length() < count; i++) {
            char value = source.charAt(i);

            if (Character.isDigit(value)) {
                digits.append(value);
            }
        }

        while (digits.length() < count) {
            digits.append('0');
        }

        return digits.toString();
    }
}