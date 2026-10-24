--
-- Licensed under the Apache License, Version 2.0. See LICENSE in the repository root.
--
-- vobs-fapi: kiểm tra mã truy cập gắn với chứng thư client (RFC 8705), theo FAPI 2.0 Security Profile.
--
-- Đối tác kết nối gateway bằng mTLS (đối tượng SSL của APISIX có client.ca) và gửi mã truy cập do Keycloak cấp cho
-- đúng chứng thư đó. Token có claim cnf["x5t#S256"]: SHA-256 của chứng thư client (dạng DER), mã hóa base64url.
-- Plugin so claim này với chứng thư client của kết nối hiện tại. Token bị lộ không dùng được từ kết nối khác.
--
-- Thứ tự: openid-connect (rewrite, 2599) xác thực token -> vobs-fapi (access, 1500) -> limit-count (1002)
-- -> vobs-consent (999). Mọi trường hợp thiếu hoặc không khớp đều từ chối (fail closed).
--
local core = require("apisix.core")
local resty_sha256 = require("resty.sha256")
local identity = require("apisix.plugins.vobs.identity")
local ngx = ngx

local plugin_name = "vobs-fapi"

local schema = {
    type = "object",
    properties = {},
}

local _M = {
    version = 0.1,
    priority = 1500,
    name = plugin_name,
    schema = schema,
}

function _M.check_schema(conf)
    return core.schema.check(schema, conf)
end

local function unauthorized(reason)
    ngx.header["WWW-Authenticate"] = 'Bearer error="invalid_token", error_description="' .. reason .. '"'
    return 401, {
        type = "https://vobs.io/problems/" .. reason:gsub("_", "-"),
        title = "Mã truy cập không gắn với chứng thư client của kết nối này",
        status = 401,
        reason = reason,
    }
end

-- SHA-256 của chứng thư (DER), base64url không đệm: giá trị x5t#S256 theo RFC 8705 mục 3.1
local function thumbprint(pem)
    local body = pem:gsub("%-%-%-%-%-[^%-]+%-%-%-%-%-", ""):gsub("%s", "")
    local der = ngx.decode_base64(body)
    if not der then
        return nil
    end
    local sha256 = resty_sha256:new()
    sha256:update(der)
    local encoded = ngx.encode_base64(sha256:final())
    return (encoded:gsub("%+", "-"):gsub("/", "_"):gsub("=", ""))
end

function _M.access(conf, ctx)
    if ngx.var.ssl_client_verify ~= "SUCCESS" then
        return unauthorized("client_certificate_required")
    end
    local claims = identity.claims(ctx)
    if not claims then
        core.log.error("vobs-fapi: không có payload token; cần openid-connect với set_userinfo_header chạy trước")
        return unauthorized("token_claims_unavailable")
    end
    local bound = type(claims.cnf) == "table" and claims.cnf["x5t#S256"]
    if type(bound) ~= "string" or bound == "" then
        return unauthorized("token_not_certificate_bound")
    end
    local presented = thumbprint(ngx.var.ssl_client_raw_cert or "")
    if not presented or presented ~= bound then
        return unauthorized("certificate_binding_mismatch")
    end
end

return _M
