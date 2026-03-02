FROM gradle:8.14-jdk21 AS build

WORKDIR /build
ENV GRADLE_USER_HOME=/home/gradle/.gradle
COPY --parents gradlew gradlew.bat **/settings.gradle* gradle.properties \
               build-logic/src gradle/ **/build.gradle* \
               ./

RUN --mount=type=cache,target=${GRADLE_USER_HOME} ./gradlew --no-daemon --no-configuration-cache --info resolveDeps

COPY apps apps
COPY libs libs
RUN --mount=type=cache,target=${GRADLE_USER_HOME} ./gradlew --info --no-configuration-cache --no-daemon bootJar

FROM eclipse-temurin:21-jre-noble AS runtime-base
WORKDIR /app

FROM runtime-base AS api
COPY --from=build /build/apps/api/build/libs/*api*.jar api.jar
ENTRYPOINT ["java", "-jar", "api.jar"]

FROM runtime-base AS worker
COPY --from=build /build/apps/worker/build/libs/*worker*.jar worker.jar
ENTRYPOINT ["java", "-jar", "worker.jar"]
