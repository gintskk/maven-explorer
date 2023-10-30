FROM maven:3.9.4-eclipse-temurin-11-alpine

WORKDIR /usr/local/runme
COPY target/lib lib
# having multiple .jars (e.g., *-sources.jar) breaks this cmd
COPY target/*.jar server.jar

ENTRYPOINT exec java $JAVA_OPTS -jar server.jar $0 $@
