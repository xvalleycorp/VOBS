# Giấy phép của thư viện không kèm file giấy phép trong JAR

Image Docker của `vobs-consent` phân phối lại các thư viện lúc chạy, nên phải kèm văn bản giấy phép của từng thư
viện (Apache-2.0 mục 4(a); BSD-3-Clause yêu cầu tái hiện thông báo bản quyền khi phân phối dạng nhị phân). Phần lớn
thư viện có sẵn `META-INF/LICENSE` trong JAR. Các thư viện dưới đây thì không, nên văn bản giấy phép được lấy nguyên
văn từ repo gốc, đúng tag của phiên bản đang dùng. Các repo này không có file `NOTICE` ở tag tương ứng.

Tên thư mục là `groupId.artifactId`, hoặc chỉ `groupId` khi dùng chung cho mọi artifact của nhóm. Khi build image,
JAR nào không có file giấy phép bên trong thì lấy ở đây; không có ở cả hai nơi thì build dừng
([Dockerfile](../../../Dockerfile)). Khi nâng phiên bản một thư viện trong bảng, tải lại file từ tag mới và đối chiếu.

| Thư mục | Thư viện | Giấy phép | Nguồn |
| --- | --- | --- | --- |
| `dev.cel` | `dev.cel:cel`, `common`, `compiler`, `protobuf`, `runtime`, `v1alpha1` 0.14.0 | Apache-2.0 | https://github.com/cel-expr/cel-java/blob/v0.14.0/LICENSE |
| `com.google.protobuf.protobuf-java` | `com.google.protobuf:protobuf-java` 4.35.1 | BSD-3-Clause | https://github.com/protocolbuffers/protobuf/blob/v35.1/LICENSE |
| `com.google.re2j.re2j` | `com.google.re2j:re2j` 1.8 | BSD-3-Clause (giấy phép của Go) | https://github.com/google/re2j/blob/re2j-1.8/LICENSE |
| `org.antlr.antlr4-runtime` | `org.antlr:antlr4-runtime` 4.13.2 | BSD-3-Clause | https://github.com/antlr/antlr4/blob/4.13.2/LICENSE.txt |
| `org.yaml.snakeyaml` | `org.yaml:snakeyaml` 2.6 | Apache-2.0 | https://bitbucket.org/snakeyaml/snakeyaml/src/snakeyaml-2.6/LICENSE.txt |
| `com.google.guava.listenablefuture` | `com.google.guava:listenablefuture` 9999.0 (JAR rỗng của Guava) | Apache-2.0 | https://github.com/google/guava/blob/v33.6.0/LICENSE |
| `com.google.auto.value.auto-value-annotations` | `com.google.auto.value:auto-value-annotations` 1.11.1 | Apache-2.0 | https://github.com/google/auto/blob/auto-value-1.11.1/LICENSE |
| `com.google.errorprone.error_prone_annotations` | `com.google.errorprone:error_prone_annotations` 2.47.0 | Apache-2.0 | https://github.com/google/error-prone/blob/v2.47.0/COPYING |
| `com.google.j2objc.j2objc-annotations` | `com.google.j2objc:j2objc-annotations` 3.1 | Apache-2.0 | https://github.com/google/j2objc/blob/3.1/LICENSE |
| `org.jspecify.jspecify` | `org.jspecify:jspecify` 1.0.0 | Apache-2.0 | https://github.com/jspecify/jspecify/blob/v1.0.0/LICENSE |

Các thư viện này đến cùng [RuleStone](https://github.com/xvalleycorp/RuleStone) (bộ máy bảng quyết định).
