# Spring Boot Scheduled Consumer

This standalone Maven project verifies that GhostWork `0.9.0` works as a real
consumer dependency together with:

* `ghostwork`
* `ghostwork-spring`
* `ghostwork-spring-webmvc`
* `ghostwork-dashboard-spring`

The test starts an embedded Spring Boot server, executes an `@Scheduled` method,
and verifies the schedule definition, execution operation, root task, MVC
tracking, dashboard UI, report API, and schedules API.

Install the four library artifacts locally, then run:

```bash
mvn clean verify
```

The `Consumer Compatibility` GitHub Actions workflow performs the complete
cross-repository build on a clean runner every day and on every core push or
pull request.
