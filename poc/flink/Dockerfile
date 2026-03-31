# ─────────────────────────────────────────────────────────────────
#  Flink Docker Image
#  Base : flink:1.17.1-scala_2.12-java11
#  Adds : Python 3, PyFlink, Kafka connector, JDBC connector,
#          PostgreSQL driver
# ─────────────────────────────────────────────────────────────────
FROM flink:1.17.1-scala_2.12-java11

# ── System packages ───────────────────────────────────────────────
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip python3-dev \
        build-essential \
        default-jdk-headless \
        wget curl ca-certificates && \
    ln -sf /usr/bin/python3 /usr/bin/python && \
    rm -rf /var/lib/apt/lists/*

# ── Fix JDK include path for pemja (PyFlink dependency) ──────────
# Flink base image uses eclipse-temurin JRE at /opt/java/openjdk.
# pemja needs JDK include headers; create symlink from installed JDK.
RUN JDK_INCLUDE=$(find /usr/lib/jvm -name "jni.h" 2>/dev/null | head -1 | xargs dirname 2>/dev/null) && \
    if [ -n "$JDK_INCLUDE" ]; then \
        mkdir -p /opt/java/openjdk && \
        ln -sf "$JDK_INCLUDE" /opt/java/openjdk/include; \
    fi

# ── PyFlink (must match Flink version) ───────────────────────────
RUN pip3 install --no-cache-dir --upgrade pip setuptools wheel && \
    pip3 install --no-cache-dir \
        "apache-flink==1.17.1" \
        "psycopg2-binary==2.9.9" \
        "requests==2.31.0"

# ── Download connector JARs into Flink lib ────────────────────────
ENV FLINK_LIB=/opt/flink/lib

# 1. Kafka SQL connector
RUN wget -q -P ${FLINK_LIB} \
    "https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/1.17.1/flink-sql-connector-kafka-1.17.1.jar"

# 2. JDBC connector
RUN wget -q -P ${FLINK_LIB} \
    "https://repo1.maven.org/maven2/org/apache/flink/flink-connector-jdbc/3.1.1-1.17/flink-connector-jdbc-3.1.1-1.17.jar"

# 3. PostgreSQL JDBC driver (TimescaleDB + PostgreSQL sinks)
RUN wget -q -P ${FLINK_LIB} \
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar"

# ── Copy job files ────────────────────────────────────────────────
COPY jobs/ /opt/flink/jobs/

ENV PYTHONPATH=/opt/flink/jobs:${PYTHONPATH}
