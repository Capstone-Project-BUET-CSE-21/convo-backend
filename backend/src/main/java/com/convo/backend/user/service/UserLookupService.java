package com.convo.backend.user.service;

import com.convo.backend.auth.dto.PublicUserResponse;
import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, cross-user name resolution for convo-file-sharing (through
 * InternalUserController), which embeds senders', recipients' and
 * downloaders' names in its chain-history and download responses,
 * including people the viewer never met in a call.
 *
 * Deliberately returns PublicUserResponse (id + displayName only, no
 * email): unlike /api/backend/auth/me, it answers for arbitrary user ids.
 */
@Service
public class UserLookupService {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Silently drops ids that don't resolve (deleted/unknown user) rather than
    // erroring the whole batch — the frontend renders a generic fallback
    // label for any id missing from the response, which is the better UX
    // for a chain hop whose sender account no longer exists.
    @Transactional(readOnly = true)
    public List<PublicUserResponse> getByIds(List<UUID> ids) {
        return userRepository.findAllById(ids).stream()
                .map(this::toPublicResponse)
                .toList();
    }

    private PublicUserResponse toPublicResponse(User user) {
        return new PublicUserResponse(user.getId(), user.getDisplayName());
    }
}
