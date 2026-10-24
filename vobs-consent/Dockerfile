# Image của service Quản lý sự đồng ý. Build từ thư mục gốc repo (cần POM cha):
#   docker build -f vobs-consent/Dockerfile -t vobs-consent .

FROM maven:3.9.16-eclipse-temurin-21 AS build
# RuleStone chưa có trên Maven Central: cài từ mã nguồn tại commit cố định (cần git). Đặt trước khi chép mã nguồn
# VOBS để Docker dùng lại lớp này khi chỉ mã VOBS thay đổi.
RUN apt-get update \
 && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/*
COPY scripts/install-rulestone.sh /tmp/install-rulestone.sh
RUN /tmp/install-rulestone.sh
WORKDIR /src
COPY pom.xml LICENSE NOTICE ./
COPY vobs-model vobs-model
COPY vobs-vietqr vobs-vietqr
COPY vobs-iso20022 vobs-iso20022
COPY vobs-switch vobs-switch
COPY vobs-consent vobs-consent
COPY vobs-hub vobs-hub
# Chỉ build module consent và các module nó cần. Profile license-check làm build dừng nếu có dependency mang giấy
# phép ngoài danh sách cho phép (docs/licensing.md); dependency lúc chạy được chép vào target/dependency, tên file
# dạng groupId.artifactId.jar.
RUN mvn -B -q -pl vobs-consent -am -Plicense-check -DskipTests verify dependency:copy-dependencies \
      -DincludeScope=runtime -Dmdep.prependGroupId=true -Dmdep.stripVersion=true
# Giấy phép của phần mềm bên thứ ba đi kèm image: danh sách THIRD-PARTY.txt và file giấy phép, thông báo
# (META-INF/LICENSE*, NOTICE*, licenses/) lấy nguyên văn từ từng JAR, như các giấy phép này yêu cầu khi phân phối lại.
# JAR không có file giấy phép bên trong thì lấy văn bản từ vobs-consent/src/license/third-party, theo
# groupId.artifactId rồi tới groupId; không có ở cả hai nơi thì build dừng.
RUN mkdir -p /licenses/third-party \
 && cp LICENSE NOTICE /licenses/ \
 && cp vobs-consent/target/generated-sources/license/THIRD-PARTY.txt /licenses/third-party/ \
 && extra=/src/vobs-consent/src/license/third-party \
 && for jar in vobs-consent/target/dependency/*.jar; do \
      name="$(basename "$jar" .jar)"; dir="/licenses/third-party/$name"; mkdir -p "$dir"; \
      (cd "$dir" && jar xf "/src/$jar" META-INF/LICENSE META-INF/LICENSE.txt META-INF/LICENSE.md \
         META-INF/NOTICE META-INF/NOTICE.txt META-INF/NOTICE.md META-INF/licenses); \
      if [ -z "$(ls -A "$dir")" ]; then \
        key="$name"; \
        while [ ! -f "$extra/$key/LICENSE" ] && [ "$key" != "${key%.*}" ]; do key="${key%.*}"; done; \
        [ -f "$extra/$key/LICENSE" ] || { echo "Không tìm thấy file giấy phép cho $jar" >&2; exit 1; }; \
        cp "$extra/$key/LICENSE" "$dir/"; \
      fi; \
    done

FROM eclipse-temurin:21.0.12.1_1-jre-noble
# Cài bản vá bảo mật của Ubuntu lúc build: tag base image có thể chậm hơn bản vá vài ngày (ví dụ OpenSSL),
# và CI quét image bằng Trivy, dừng khi còn lỗ hổng HIGH, CRITICAL đã có bản vá.
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home vobs
WORKDIR /app
COPY --from=build /src/vobs-consent/target/vobs-consent-*.jar /app/lib/
COPY --from=build /src/vobs-consent/target/dependency/ /app/lib/
COPY --from=build /licenses/ /app/licenses/
USER 10001
# Trong container phải nghe trên mọi địa chỉ; cổng đối tác 8080, cổng nội bộ 8081
ENV VOBS_CONSENT_BIND=0.0.0.0
EXPOSE 8080 8081
ENTRYPOINT ["java", "-cp", "/app/lib/*", "io.vobs.consent.http.ConsentServerMain"]
