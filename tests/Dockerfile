# datazeus-tests — JDK 17 + Maven 3.9.9 + Groovy 4.0.22: run the publish gate (*Spec) or the koans
# (*Koans) with nothing but Docker installed.
#
# THE VERSIONS ARE THE ONES THIS MODULE PINS: maven.compiler.release 17 and groovy 4.0.22 in
# pom.xml (Spock 2.3-groovy-4.0), Maven 3.9.9 in .mvn/wrapper. Maven is installed rather than left
# to ./mvnw, so a run does not download a distribution first. Groovy itself is compiled by
# gmavenplus from the pom; the CLI is here for ad-hoc scripts against the same version.
#
# NOT ALPINE: duckdb_jdbc ships a glibc native library.
#
# BUILD (from the repository root):
#   docker build -t datazeus-tests tests
#
# RUN — mount the WHOLE repository, not just tests/: every spec reads its lesson's own SQL from
# ../courses/**/scripts/*.sql.
#   docker run --rm -v "$PWD":/datazeus -v datazeus-m2:/root/.m2 \
#     -v /var/run/docker.sock:/var/run/docker.sock --network host \
#     datazeus-tests -B test                                                  # the whole gate
#   ... datazeus-tests -B test -Dtest="datazeus.learnsql.series1._50.*Spec"   # one lesson
#   ... datazeus-tests -B test -Pkoans                                        # the koans instead
#
#   -v datazeus-m2       the dependency cache, so only the first run downloads
#   docker.sock          the PostgreSQL half of the gate: Testcontainers starts postgres:16.2 as a
#   --network host       SIBLING container, seeded from the DuckDB file, and reaches it on localhost
#   -e PGHOST=localhost  check against a Northwind PostgreSQL that is ALREADY RUNNING (e.g. the one
#                        DataPallas starts, the one you see in CloudBeaver) instead of the throwaway
#                        one; PGPORT, PGDATABASE, PGUSER and PGPASSWORD work the same way
#
# On Windows (PowerShell) use ${PWD} for the mount. --network host needs host networking enabled in
# Docker Desktop; without it, run the gate with zeus.bat and a local JDK 17 instead.
FROM eclipse-temurin:17-jdk-jammy

ARG MAVEN_VERSION=3.9.9
ARG GROOVY_VERSION=4.0.22

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      | tar -xz -C /opt \
 && ln -s "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven

RUN curl -fsSL -o /tmp/groovy.zip "https://archive.apache.org/dist/groovy/${GROOVY_VERSION}/distribution/apache-groovy-binary-${GROOVY_VERSION}.zip" \
 && unzip -q /tmp/groovy.zip -d /opt \
 && rm /tmp/groovy.zip \
 && ln -s "/opt/groovy-${GROOVY_VERSION}" /opt/groovy

ENV MAVEN_HOME=/opt/maven \
    GROOVY_HOME=/opt/groovy \
    PATH=/opt/maven/bin:/opt/groovy/bin:$PATH

WORKDIR /datazeus/tests
ENTRYPOINT ["mvn"]
CMD ["-B", "test"]
