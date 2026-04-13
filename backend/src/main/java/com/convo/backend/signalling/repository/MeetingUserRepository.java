package com.convo.backend.signalling.repository;

import com.convo.backend.auth.entity.User;
import com.convo.backend.signalling.entity.Meeting;
import com.convo.backend.signalling.entity.MeetingUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingUserRepository extends JpaRepository<MeetingUser, Long> {

    Optional<MeetingUser> findByMeetingAndUser(Meeting meeting, User user);

    Optional<MeetingUser> findByMeetingAndUserAndLeftAtIsNull(Meeting meeting, User user);

    long countByMeetingAndLeftAtIsNull(Meeting meeting);
}
