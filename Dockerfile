FROM gradle:6.6-jdk11 AS build
ARG app_version=0.0.0
COPY ./ .
RUN gradle build -Prelease_version=${app_version}

RUN mkdir /home/app
RUN mkdir /home/app/config
RUN cp ./build/libs/*.jar /home/app/application.jar
RUN cp logback.xml /home/app/config/logback.xml

FROM openjdk:12-alpine
COPY --from=build /home/app /home/app
COPY --from=build /home/app/config /home/app/config

WORKDIR /home/app/
ENTRYPOINT ["java", "-Dlogback.configurationFile=./config/logback.xml", "-jar", "/home/app/application.jar"]