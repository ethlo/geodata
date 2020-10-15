FROM openjdk:8-jdk-alpine
ENV LANG C.UTF-8
ENV JAVA_TOOL_OPTIONS -Dfile.encoding=UTF8
VOLUME /tmp
ARG JAR_FILE
ADD ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Xmx5G","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]