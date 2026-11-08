package com.acme.sportplatform.protocol;

import java.util.List;
import java.util.UUID;

public interface RegistrationProtocolAccess {
    List<ConfirmedRegistrationView> findConfirmedByEventDisciplineId(UUID eventDisciplineId);

    record ConfirmedRegistrationView(UUID registrationId, String registrationMeta, UUID participantProfileId,
                                     String participantSnapshot) {
        public ConfirmedRegistrationView(UUID registrationId, String registrationMeta, UUID participantProfileId) {
            this(registrationId, registrationMeta, participantProfileId, null);
        }
        /** Compatibility for seeding-only callers. Does not invent an athlete identity. */
        public ConfirmedRegistrationView(UUID registrationId, String registrationMeta) {
            this(registrationId, registrationMeta, null);
        }
        /** Must be called by the scheduling adapter before building Heat.athleteIds. */
        public UUID requireParticipantProfileId() {
            if (participantProfileId == null) {
                throw new IllegalStateException("Participant profile ID is required for scheduling; registrationId=" + registrationId);
            }
            return participantProfileId;
        }
    }
}
