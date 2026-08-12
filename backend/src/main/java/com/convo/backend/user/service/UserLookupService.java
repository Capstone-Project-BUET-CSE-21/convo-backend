package com.convo.backend.user.service;

import com.convo.backend.auth.dto.PublicUserResponse;
import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, cross-user name resolution. Exists specifically so the
 * file-sharing trace screen (convo-file-sharing's ChainHistoryResponseDto
 * only carries raw senderId UUIDs) can show a real display name for every
 * hop in a chain, not just hops from a meeting the viewer personally sat
 * in — see identity/senderIdentity.js on the frontend, which previously
 * had no lookup source at all and fell back to "Unknown sender" for any
 * hop outside the viewer's own live meeting signaling.
 *
 * Deliberately returns PublicUserResponse (id + displayName only, no
 * email) since, unlike /api/auth/me, this is reachable for arbitrary user
 * ids by any authenticated caller.
 */
@Service
public class UserLookupService {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PublicUserResponse getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toPublicResponse(user);
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