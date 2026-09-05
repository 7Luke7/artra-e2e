# syntax=docker/dockerfile:1
#
# The test runner: JDK, Maven, and the suite.
#
# It runs inside the compose network so the tests reach the grid, the mail
# server and the database by service name, exactly as they do in CI - the
# alternative, running Maven on the host, works too but leaves a class of
# "works on my machine" failures that only show up in the pipeline.

FROM eclipse-temurin:25-jdk-noble

ARG MAVEN_VERSION=3.9.16

LABEL org.opencontainers.image.title="artra-e2e test runner" \
      org.opencontainers.image.description="Selenium + JUnit 5 end-to-end suite for the Artra platform"

RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

ADD --checksum=sha256:80ffca22aed9e8b9713a232f3394fd81d7f20322df75efdb2b047dbd3e3a23bb \
    https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    /tmp/maven.tar.gz
RUN tar -xzf /tmp/maven.tar.gz -C /opt && rm /tmp/maven.tar.gz

ENV PATH=/opt/apache-maven-${MAVEN_VERSION}/bin:$PATH \
    MAVEN_OPTS="-Dfile.encoding=UTF-8"

WORKDIR /artra-e2e

# Dependencies are resolved into the image so a run does not start with a
# download. Only the POM is copied first, so editing a test never invalidates
# this layer.
#
# `verify -DskipTests -DskipITs` rather than `dependency:go-offline`: the latter
# resolves the project's declared dependencies but not the plugins' own, so the
# first real run still went to Maven Central for junit-platform-launcher. Going
# through the actual lifecycle warms everything the run will ask for.
COPY pom.xml .
RUN mvn -B -q verify -DskipTests -DskipITs

COPY src ./src

CMD ["tail", "-f", "/dev/null"]
