package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.entity.EventLog;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.Reaction;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.entity.type.PostCategory;
import com.github.ferigeek.sarv.exception.UserNotFoundException;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    @Async
    public void logPostView(String username, Post post) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setPost(post);
        eventLog.setUser(user);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.VIEW_POST);

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
    public CompletableFuture<EventLog> logPostCreation(String username, Post post) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setPost(post);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.CREATE_POST);

        return CompletableFuture.completedFuture(eventLogRepository.save(eventLog));
    }

    @Async
    public void logFeedRequest(String username, String feedType) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setCreatedAt(OffsetDateTime.now());
        eventLog.setEventType(EventType.REQUEST_FEED);
        Map<String, String> metadata = Map.of("feed_type", feedType);

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
    Set `isUnfollow` to 0 for following and 1 for unfollowing.
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

    public void logLogin(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setEventType(EventType.LOGIN);
        eventLog.setCreatedAt(OffsetDateTime.now());

        eventLogRepository.save(eventLog);
    }

    public void logRegister(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        EventLog eventLog = new EventLog();

        eventLog.setUser(user);
        eventLog.setEventType(EventType.REGISTER);
        eventLog.setCreatedAt(OffsetDateTime.now());

        eventLogRepository.save(eventLog);
    }

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
