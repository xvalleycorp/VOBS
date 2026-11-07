# Keycloak kèm phần mở rộng của VOBS (trang duyệt consent, claim consent_id, giao diện vobs). Build từ thư mục gốc
# repo (cần POM cha):
#   docker build -f vobs-keycloak/Dockerfile -t vobs-keycloak .
# Chỉ thêm một JAR vào thư mục providers của image Keycloak chính thức; JAR không kèm thư viện bên thứ ba.

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml LICENSE NOTICE ./
COPY vobs-model vobs-model
COPY vobs-vietqr vobs-vietqr
COPY vobs-iso20022 vobs-iso20022
COPY vobs-switch vobs-switch
COPY vobs-consent vobs-consent
COPY vobs-hub vobs-hub
COPY vobs-keycloak vobs-keycloak
RUN mvn -B -q -pl vobs-keycloak -am -Plicense-check -DskipTests verify

FROM quay.io/keycloak/keycloak:26.8.0
COPY --from=build /src/vobs-keycloak/target/vobs-keycloak-*.jar /opt/keycloak/providers/vobs-keycloak.jar
COPY --from=build /src/LICENSE /src/NOTICE /opt/vobs/licenses/
