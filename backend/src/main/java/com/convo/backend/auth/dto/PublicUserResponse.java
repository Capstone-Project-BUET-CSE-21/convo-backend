package com.convo.backend.auth.dto;

import java.util.UUID;

/**
 * Deliberately excludes email — unlike UserProfileResponse (returned only
 * from /api/auth/me for the caller's own account), this shape is returned
 * for arbitrary user ids to any authenticated caller, so it must not leak
 * anything more sensitive than a display name. Used by the file-sharing
 * trace screen to resolve a chain hop's senderId into a human-readable
 * name, including for hops the viewer never shared a live meeting with.
 */
public record PublicUserResponse(
        UUID id,
        String displayName
) {
}