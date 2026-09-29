package com.convo.backend.signalling.repository;

import com.convo.backend.auth.entity.User;
import com.convo.backend.signalling.entity.Meeting;
import com.convo.backend.signalling.entity.MeetingUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingUserRepository extends JpaRepository<MeetingUser, Long> {

    Optional<MeetingUser> findByMeetingAndUser(Meeting meeting, User user);

    Optional<MeetingUser> findByMeetingAndUserAndLeftAtIsNull(Meeting meeting, User user);

    long countByMeetingAndLeftAtIsNull(Meeting meeting);

    // Every membership row for a meeting, ever — including participants who
    // have since left. Backs the internal participants endpoint
    // convo-audio-watermark calls instead of keeping its own copy of this
    // data; a past participant must keep counting after leaving, since a
    // recording can be checked long after the meeting ended (see
    // InternalMeetingController).
    List<MeetingUser> findByMeeting(Meeting meeting);
}
