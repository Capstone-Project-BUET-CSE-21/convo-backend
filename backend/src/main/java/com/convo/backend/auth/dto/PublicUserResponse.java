package com.convo.backend.auth.dto;

import java.util.UUID;

/**
 * Deliberately excludes email — unlike UserProfileResponse (returned only
 * from /api/backend/auth/me for the caller's own account), this shape is returned
 * for arbitrary user ids, and convo-file-sharing passes the names on to any
 * logged-in viewer of a file's history, so it must not carry anything more
 * sensitive than a display name.
 */
public record PublicUserResponse(
        UUID id,
        String displayName
) {
}