package com.convo.backend.signalling.service;

import com.convo.backend.auth.entity.User;
import com.convo.backend.auth.repository.UserRepository;
import com.convo.backend.signalling.entity.Meeting;
import com.convo.backend.signalling.entity.MeetingUser;
import com.convo.backend.signalling.repository.MeetingRepository;
import com.convo.backend.signalling.repository.MeetingUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class MeetingLifecycleService {

    private final MeetingRepository meetingRepository;
    private final MeetingUserRepository meetingUserRepository;
    private final UserRepository userRepository;

    private void upsertMeetingUser(Meeting meeting, User user, String role) {
        MeetingUser meetingUser = meetingUserRepository.findByMeetingAndUser(meeting, user)
                .orElseGet(() -> {
                    MeetingUser mu = new MeetingUser();
                    mu.setMeeting(meeting);
                    mu.setUser(user);
                    return mu;
                });

        meetingUser.setRole(role);
        meetingUser.setJoinedAt(Instant.now());
        meetingUser.setLeftAt(null);
        meetingUserRepository.save(meetingUser);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    public MeetingLifecycleService(
            MeetingRepository meetingRepository,
            MeetingUserRepository meetingUserRepository,
            UserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.meetingUserRepository = meetingUserRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void handleMeetingStart(String meetingCode, UUID userId) {
        User user = getUserOrThrow(userId);

        Meeting meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseGet(() -> {
                    Meeting m = new Meeting();
                    m.setMeetingCode(meetingCode);
                    m.setStatus("active");
                    m.setHostUser(user);
                    m.setStartedAt(Instant.now());
                    m.setEndedAt(null);
                    return m;
                });

        if (meeting.getHostUser() == null) {
            meeting.setHostUser(user);
        }
        meeting.setStatus("active");
        meeting.setStartedAt(Instant.now());
        meeting.setEndedAt(null);

        Meeting savedMeeting = meetingRepository.save(meeting);
        upsertMeetingUser(savedMeeting, user, "host");
    }

    @Transactional
    public void makeMeetingEntry(String command, String meetingCode, UUID userId) {
        if (command == null || meetingCode == null || meetingCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid meeting entry request");
        }

        String normalizedCommand = command.trim().toLowerCase();
        switch (normalizedCommand) {
            case "start" -> handleMeetingStart(meetingCode, userId);
            case "join" -> handleMeetingJoin(meetingCode, userId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported meeting command");
        }
    }

    @Transactional
    public void handleMeetingJoin(String meetingCode, UUID userId) {
        User user = getUserOrThrow(userId);
        Meeting meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));

        upsertMeetingUser(meeting, user, "participant");
    }

    @Transactional
    public void handleMeetingLeave(String meetingCode, UUID userId) {
        Meeting meeting = meetingRepository.findByMeetingCode(meetingCode).orElse(null);
        if (meeting == null) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        meetingUserRepository.findByMeetingAndUserAndLeftAtIsNull(meeting, user)
                .ifPresent(participant -> {
                    participant.setLeftAt(Instant.now());
                    meetingUserRepository.save(participant);
                });

        if (meetingUserRepository.countByMeetingAndLeftAtIsNull(meeting) == 0) {
            meeting.setStatus("ended");
            meeting.setEndedAt(Instant.now());
            meetingRepository.save(meeting);
        }
    }
}
