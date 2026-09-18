package com.convo.backend.signalling.dto;

import java.time.Instant;
import java.util.UUID;

public record MeetingParticipantDto(UUID userId, String displayName, Instant joinedAt) {}
