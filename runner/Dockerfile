FROM maven:3.9.4-eclipse-temurin-11-alpine

# disable Maven's default blocker for http repositories
RUN cat /usr/share/maven/conf/settings.xml \
    # enable sed multiline replacements 
    | tr '\n' '\r' \
    | sed -r 's/(<mirror>\s+<id>maven-default-http-blocker.*?<\/mirror>)/<!-- disabled to unblock http repositories \1 -->/g' \
    | tr '\r' '\n' \
    > /usr/share/maven/conf/settings.xml

WORKDIR /usr/local/runme
COPY target/lib lib
# having multiple .jars (e.g., *-sources.jar) breaks this cmd
COPY target/*.jar server.jar

ENTRYPOINT exec java $JAVA_OPTS -jar server.jar $0 $@
