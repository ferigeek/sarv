package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.entity.EventLog;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class EventLogService {

    private final EventLogRepository eventLogRepository;
    private final UserRepository userRepository;

    @Autowired
    public EventLogService(
            EventLogRepository eventLogRepository,
            UserRepository userRepository) {
        this.eventLogRepository = eventLogRepository;
        this.userRepository = userRepository;
    }

    public void logPostView(String username, Post post) {
        logPostView(username, post, null);
    }

    @Async
    public void logPostView(String username, Post post, UUID sessionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setPost(post);
        eventLog.setUser(user);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.VIEW_POST);
        eventLog.setSessionId(sessionId);

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logProfileView(String username, User targetUser) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setTargetUser(targetUser);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.VIEW_PROFILE);

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logFeedRequest(String username, String feedType) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.REQUEST_FEED);
        Map<String, Object> metadata = Map.of("feed_type", feedType);
        eventLog.setMetadata(metadata);

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logReaction(String username, Post post, short reactionType) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setPost(post);
        eventLog.setCreatedAt(OffsetDateTime.now());

        switch (reactionType) {
            case Reaction.LIKE:
                eventLog.setEventType(EventType.LIKE_POST);
                break;
            case Reaction.DISLIKE:
                eventLog.setEventType(EventType.DISLIKE_POST);
                break;
            default:
                throw new IllegalArgumentException("reactionType is invalid");
        }

        eventLogRepository.save(eventLog);
    }

    /*
    Logs both following and unfollowing events.
     */
    @Async
    public void logFollow(User user, User targetUser, boolean isUnfollow) {
        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setTargetUser(targetUser);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(isUnfollow ? EventType.UNFOLLOW_USER : EventType.FOLLOW_USER);

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logLogin(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setEventType(EventType.LOGIN);
        eventLog.setCreatedAt(OffsetDateTime.now());

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logRegister(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setEventType(EventType.REGISTER);
        eventLog.setCreatedAt(OffsetDateTime.now());

        eventLogRepository.save(eventLog);
    }

    @Async
    public void logPostCreation(String username, Post post, PostCategory postCategory) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setPost(post);
        eventLog.setCreatedAt(OffsetDateTime.now());

        switch (postCategory) {
            case PostCategory.NORMAL -> eventLog.setEventType(EventType.CREATE_POST);
            case PostCategory.COMMENT -> eventLog.setEventType(EventType.CREATE_COMMENT);
            case PostCategory.QUOTE -> eventLog.setEventType(EventType.QUOTE_POST);
            case PostCategory.REPOST -> eventLog.setEventType(EventType.REPOST_POST);
        }

        eventLogRepository.save(eventLog);
    }
}
