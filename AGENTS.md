# Intoduction and Purpose

Sarv is a server side application being built for my computer science bachelor's project.

This application is a social media platform, similar to X (formerly Twitter). Though the project is small, but it tries to put real world problems into consideration. So testing, documentation, observability and monitoring, scalability, and reliability are also of top priorities


# Project Structure

The project is separated by responsibility, subject and proper tools.

There are thee main services:

- Core Backend: Handles the core business logic, and manges data, posts, users, and authentication. This is the service that communicates with the clients, created with Spring Boot and Java.
- Recommendation: Generates the feed and recommends posts to the users. Created using FastAPI and Python.
- Analytics: Gives reports of the usage of the platform, like number of active users, hot topics, influencial people, peak usage, ... . Planned to build in Python.

## Other components
- PostgreSQL: Main database of the system.
- Redis: Planned to be used for caching and rate limiting.
- Grafana & Prometheus: Planned to be used for monitoring.

# Repository Layout

```
backend/
intelligence/ 
    recommendation/
    analytics/
docs/    Docs in `.md` files and mkdocs to serve them
```

# Rules

- Git commit in only one specific topic with this structure:
    1. Type of the change(feat, fix, ...) + The conceptual section of the project in paranthesis(backend/intelligence) + Short informful summary of the change
        - e.g. feat(backend): Add JWT authentication
    2. Explanation of what has changes, why, and what is different now
    3. Things to consider or noticable if any exists
- Code should be simple, readable, and easily understandable
- Comments and docs in the code are only required when there is something important to notice, a piece of code that might be hard to understand, or something unexpected.
- All packages and dependencies should be added by the developer and not the agent. Any file related to dependencies should be kept untouched.

# Run

`docker-compose.yaml` and `.env.example` exists at the root of the project with required environment variables.
