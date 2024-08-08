FROM gradle:8.7-jdk11 AS build
ARG app_version=0.0.0
COPY ./ .
RUN gradle --no-daemon clean build dockerPrepare -Prelease_version=${release_version}

FROM adoptopenjdk/openjdk11:alpine
WORKDIR /home
COPY --from=build /home/gradle/build/docker .
ENTRYPOINT ["/home/service/bin/service", "-Dlog4j2.configurationFile=file:/var/th2/config/log4j2.properties"]