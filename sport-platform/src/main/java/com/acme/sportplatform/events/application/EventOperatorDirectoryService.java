package com.acme.sportplatform.events.application;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.api.EventOperatorCandidateResponse;
import com.acme.sportplatform.events.api.EventOperatorMemberResponse;
import com.acme.sportplatform.identity.OperatorDirectoryLookup;

@Service
@Transactional(readOnly = true)
public class EventOperatorDirectoryService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private final EventOperatorService assignments;
    private final OperatorDirectoryLookup directory;
    public EventOperatorDirectoryService(EventOperatorService assignments, OperatorDirectoryLookup directory) {
        this.assignments = assignments; this.directory = directory;
    }

    public List<EventOperatorMemberResponse> members(UUID eventId, UUID actorId, boolean admin) {
        var saved = assignments.list(eventId, actorId, admin);
        var users = directory.findByIds(saved.stream().map(item -> item.operatorUserId()).distinct().toList());
        return saved.stream().map(item -> {
            var user = users.get(item.operatorUserId());
            return new EventOperatorMemberResponse(item.id(), item.eventId(), item.operatorUserId(),
                    item.assignedByUserId(), item.createdAt(), user == null ? null : user.email(),
                    user == null ? "Пользователь недоступен" : user.displayName(), user != null && user.eligible());
        }).toList();
    }

    public List<EventOperatorCandidateResponse> candidates(UUID eventId, String email, UUID actorId, boolean admin) {
        var saved = assignments.list(eventId, actorId, admin);
        String normalized = email == null ? "" : email.strip();
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new BusinessException("events.invalid_operator_email", "Укажите корректный полный email оператора.");
        }
        return directory.findActiveByExactEmail(normalized).map(user -> List.of(
                new EventOperatorCandidateResponse(user.userId(), user.email(), user.displayName(),
                        saved.stream().anyMatch(item -> item.operatorUserId().equals(user.userId())))))
                .orElseGet(List::of);
    }
}
