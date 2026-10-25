# Build: docker build -t vda5050-testkit .
# Run:   docker run --rm --network host vda5050-testkit record -b tcp://localhost:1883 --check -d 60
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -ntp -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 testkit
COPY --from=build /src/vda5050-testkit-cli/target/vda5050-testkit.jar /opt/vda5050-testkit/vda5050-testkit.jar
USER testkit
WORKDIR /work
ENTRYPOINT ["java", "-jar", "/opt/vda5050-testkit/vda5050-testkit.jar"]
CMD ["--help"]
