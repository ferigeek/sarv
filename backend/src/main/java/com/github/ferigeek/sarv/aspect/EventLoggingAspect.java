package com.github.ferigeek.sarv.aspect;

import com.github.ferigeek.sarv.dto.response.PostResponse;
import com.github.ferigeek.sarv.entity.EventLog;
import com.github.ferigeek.sarv.entity.Post;
import com.github.ferigeek.sarv.entity.User;
import com.github.ferigeek.sarv.entity.type.EventType;
import com.github.ferigeek.sarv.repository.EventLogRepository;
import com.github.ferigeek.sarv.repository.PostRepository;
import com.github.ferigeek.sarv.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Map;

@Aspect
@Component
public class EventLoggingAspect {

    private final EventLogRepository eventLogRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @Autowired
    public EventLoggingAspect(EventLogRepository eventLogRepository, UserRepository userRepository, PostRepository postRepository) {
        this.eventLogRepository = eventLogRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @AfterReturning(pointcut = "@annotation(logEvent)", returning = "result")
    public void logEvent(JoinPoint joinPoint, LogEvent logEvent, Object result) {
        EventType eventType = logEvent.value();

        User actor = getActor();
        if (actor == null) {
            return;
        }

        EventLog eventLog = new EventLog();
        eventLog.setUser(actor);
        eventLog.setEventType(eventType);
        eventLog.setCreatedAt(OffsetDateTime.now());

        Map<String, String> pathVariables = getPathVariables();

        switch (eventType) {
            case VIEW_POST, LIKE_POST, DISLIKE_POST -> {
                Long postId = getLongParam(joinPoint, "postId", pathVariables);
                if (postId != null) {
                    Post post = postRepository.findById(postId).orElse(null);
                    eventLog.setPost(post);
                }
            }
            case FOLLOW_USER, UNFOLLOW_USER, VIEW_PROFILE -> {
                Long userId = getLongParam(joinPoint, "userId", pathVariables);
                if (userId != null) {
                    User targetUser = userRepository.findById(userId).orElse(null);
                    eventLog.setTargetUser(targetUser);
                }
            }
            case CREATE_POST -> {
                if (result instanceof PostResponse postResponse) {
                    Long postId = postResponse.getId();
                    if (postId != null) {
                        Post post = postRepository.findById(postId).orElse(null);
                        eventLog.setPost(post);
                    }
                }
            }
            case LOGIN -> {
            }
            default -> {
            }
        }

        eventLogRepository.save(eventLog);
    }

    private User getActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username;
        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    private Map<String, String> getPathVariables() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return Map.of();
            }
            HttpServletRequest request = attrs.getRequest();
            Object attr = request.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (attr instanceof Map) {
                return (Map<String, String>) attr;
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private Long getLongParam(JoinPoint joinPoint, String paramName, Map<String, String> pathVariables) {
        String value = pathVariables.get(paramName);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Object[] args = joinPoint.getArgs();
        String[] paramNames = getParameterNames(joinPoint);
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(paramName) && args[i] instanceof Long l) {
                return l;
            }
        }
        return null;
    }

    private String[] getParameterNames(JoinPoint joinPoint) {
        try {
            var sig = (org.aspectj.lang.reflect.MethodSignature) joinPoint.getStaticPart().getSignature();
            return sig.getParameterNames();
        } catch (Exception e) {
            return new String[0];
        }
    }
}