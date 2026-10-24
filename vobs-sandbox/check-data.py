#!/usr/bin/env python3
"""Kiểm tra dữ liệu mẫu của sandbox (data/*.examples.yaml) khớp với đặc tả OpenAPI.

Mỗi phản hồi mẫu phải đúng schema của API và mã trạng thái tương ứng; tham số request phải có trong đặc tả;
metadata.version phải trùng info.version của đặc tả (khác thì Microcks bỏ qua file). Cần PyYAML và jsonschema.

    python3 vobs-sandbox/check-data.py
"""
import sys
from pathlib import Path

import jsonschema
import yaml
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "vobs-spec/openapi/vobs-open-api.yaml"
DATA = ROOT / "vobs-sandbox/data/vobs-open-api.examples.yaml"
SPEC_URI = "urn:vobs-spec"


def main() -> int:
    spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))
    data = yaml.safe_load(DATA.read_text(encoding="utf-8"))
    errors = []
    if data["metadata"]["version"] != spec["info"]["version"]:
        errors.append(f"metadata.version {data['metadata']['version']} khác info.version {spec['info']['version']}")

    # Schema được trỏ tới bằng JSON Pointer bên trong đặc tả, để các $ref (#/components/...) phân giải theo đặc tả
    registry = Registry().with_resource(SPEC_URI, Resource.from_contents(spec, default_specification=DRAFT202012))
    count = 0
    for operation, examples in data["operations"].items():
        method, path = operation.split(" ", 1)
        path_item = spec["paths"][path]
        parameters = path_item.get("parameters", []) + path_item[method.lower()].get("parameters", [])
        names = {parameter_name(spec, p) for p in parameters}
        for name, example in examples.items():
            where = f"{operation} [{name}]"
            for parameter in example["request"].get("parameters", {}):
                if parameter not in names:
                    errors.append(f"{where}: tham số {parameter} không có trong đặc tả")
            response = example["response"]
            status = str(response.get("status", "200"))
            schema = response_schema(spec, path, method.lower(), status, response["mediaType"])
            if schema is None:
                errors.append(f"{where}: đặc tả không có phản hồi {status} {response['mediaType']}")
                continue
            validator = jsonschema.Draft202012Validator(schema, registry=registry,
                                                        format_checker=jsonschema.FormatChecker())
            for error in validator.iter_errors(response["body"]):
                errors.append(f"{where}: {error.message}")
            count += 1

    for error in errors:
        print("✗", error)
    if errors:
        return 1
    print(f"Dữ liệu sandbox khớp đặc tả: {count} phản hồi mẫu")
    return 0


def parameter_name(spec, parameter):
    if "$ref" in parameter:
        parameter = spec["components"]["parameters"][parameter["$ref"].rsplit("/", 1)[1]]
    return parameter["name"]


def response_schema(spec, path, method, status, media_type):
    """Schema {"$ref": ...} trỏ tới schema của phản hồi trong đặc tả, hoặc None nếu đặc tả không có phản hồi đó."""
    response = spec["paths"][path][method]["responses"].get(status)
    if response is None:
        return None
    pointer = ["paths", path, method, "responses", status]
    if "$ref" in response:
        name = response["$ref"].rsplit("/", 1)[1]
        response = spec["components"]["responses"][name]
        pointer = ["components", "responses", name]
    if media_type not in response.get("content", {}):
        return None
    pointer += ["content", media_type, "schema"]
    escaped = "/".join(part.replace("~", "~0").replace("/", "~1") for part in pointer)
    return {"$ref": f"{SPEC_URI}#/{escaped}"}

if __name__ == "__main__":
    sys.exit(main())
