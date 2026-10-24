#!/usr/bin/env bash
# Cài RuleStone (https://github.com/xvalleycorp/RuleStone, Apache-2.0) vào kho Maven cục bộ từ mã nguồn, tại một
# commit cố định. RuleStone chưa phát hành lên Maven Central; khi có bản phát hành, bỏ script này và đặt
# rulestone.version trong pom.xml thành bản phát hành.
#
# Mã commit là hash nội dung của git: build tái lập được, và nội dung không thể bị thay mà mã commit giữ nguyên.
# Đổi commit: sửa RULESTONE_COMMIT, chạy lại script, rồi mvn verify.
set -euo pipefail

RULESTONE_REPO="https://github.com/xvalleycorp/RuleStone.git"
RULESTONE_COMMIT="5a4dc73dde760501b993de38feccf1dd2004d847"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "Cài RuleStone tại commit $RULESTONE_COMMIT"
git -C "$work" init -q
git -C "$work" fetch -q --depth 1 "$RULESTONE_REPO" "$RULESTONE_COMMIT"
git -C "$work" checkout -q FETCH_HEAD
# Chỉ các module VOBS dùng. Kiểm thử của RuleStone chạy trong CI của chính RuleStone.
mvn -B -q -f "$work/pom.xml" -pl rulestone-core -am -DskipTests install
