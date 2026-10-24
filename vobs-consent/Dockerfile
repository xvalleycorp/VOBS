# Image của service Quản lý sự đồng ý. Build từ thư mục gốc repo (cần POM cha):
#   docker build -f vobs-consent/Dockerfile -t vobs-consent .

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY vobs-model vobs-model
COPY vobs-vietqr vobs-vietqr
COPY vobs-iso20022 vobs-iso20022
COPY vobs-switch vobs-switch
COPY vobs-consent vobs-consent
COPY vobs-hub vobs-hub
# Chỉ build module consent và các module nó cần; chép dependency lúc chạy vào target/dependency
RUN mvn -B -q -pl vobs-consent -am -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:21.0.12_8-jre-noble
RUN useradd --system --uid 10001 --no-create-home vobs
WORKDIR /app
COPY --from=build /src/vobs-consent/target/vobs-consent-*.jar /app/lib/
COPY --from=build /src/vobs-consent/target/dependency/ /app/lib/
USER 10001
# Trong container phải nghe trên mọi địa chỉ; cổng đối tác 8080, cổng nội bộ 8081
ENV VOBS_CONSENT_BIND=0.0.0.0
EXPOSE 8080 8081
ENTRYPOINT ["java", "-cp", "/app/lib/*", "io.vobs.consent.http.ConsentServerMain"]
