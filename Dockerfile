FROM ubuntu:20.04 AS builder
ARG DEBIAN_FRONTEND=noninteractive

ENV LANG en_US.UTF-8

RUN apt-get update && \
    apt-get install --no-install-recommends -y curl ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Scripts adapted from 
# https://github.com/oracle/docker-images/blob/main/OracleJava/17/Dockerfile
ENV JAVA_URL=https://download.oracle.com/java/17/archive \
    JAVA_HOME=/java/jdk-17 \
    MVN_URL=https://archive.apache.org/dist/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz \
    MVN_HOME=/maven/apache-maven-3.9.1

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN set -eux; \
    ARCH="$(uname -m)" && \
    if [ "$ARCH" = "x86_64" ]; \
        then ARCH="x64"; \
    fi && \
    JAVA_PKG="$JAVA_URL"/jdk-17.0.2_linux-"$ARCH"_bin.tar.gz; \
    JAVA_SHA256="$(curl "$JAVA_PKG".sha256)"; \
    curl --output /tmp/jdk.tar.gz "$JAVA_PKG" && \
    echo "$JAVA_SHA256" */tmp/jdk.tar.gz | sha256sum -c; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract --file /tmp/jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1; \
    rm /tmp/jdk.tar.gz; \
    MVN_SHA512="$(curl "$MVN_URL".sha512)"; \
    curl --output /tmp/maven.tar.gz "$MVN_URL" && \
    echo "$MVN_SHA512" */tmp/maven.tar.gz | sha512sum -c; \
    mkdir -p "$MVN_HOME"; \
    tar --extract --file /tmp/maven.tar.gz --directory "$MVN_HOME" --strip-components 1; \
    rm /tmp/maven.tar.gz;

FROM ubuntu:20.04 AS runner
ARG DEBIAN_FRONTEND=noninteractive

ENV LANG en_US.UTF-8
ENV JAVA_HOME=/java/jdk-17 \
    MVN_HOME=/maven/apache-maven-3.9.1
ENV PATH $MVN_HOME/bin:$JAVA_HOME/bin:$PATH

COPY --from=builder $JAVA_HOME $JAVA_HOME
COPY --from=builder $MVN_HOME $MVN_HOME

RUN apt-get update && \
    apt-get install --no-install-recommends -y sudo wget unzip xz-utils \
                ca-certificates libfreetype6-dev fontconfig python3.9 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    ln -s /usr/bin/python3.9 /usr/bin/python && \
    useradd --create-home \
            --home-dir /ppt4j \
            --shell /bin/bash \
            --gid root \
            --groups sudo \
            --uid 1001 \
            ppt4j && \
    echo "ppt4j ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

USER ppt4j
WORKDIR /ppt4j
ADD . /ppt4j

RUN unzip -j ${HOME}/framework/src/main/resources/dataset/db.bin \
        -x "ser/*" "*.json" -d ${HOME}/.temp > /dev/null

CMD ["/bin/bash"]