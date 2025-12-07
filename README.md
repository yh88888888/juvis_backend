Spring Boot (Java 21) + Gradle + Web
====================================

How to run
----------

1) Ensure Java 21 is installed and on PATH.
2) Generate the Gradle wrapper JAR (since this repo omits binaries):

```bash
gradle wrapper --gradle-version 8.10.2
```

3) Build and run:

```bash
./gradlew bootRun
```

Then open `http://localhost:8080/hello`

















