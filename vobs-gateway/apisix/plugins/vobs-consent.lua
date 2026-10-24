--
-- Licensed under the Apache License, Version 2.0. See LICENSE in the repository root.
--
-- vobs-consent: kiểm tra consent trước khi chuyển request của đối tác vào upstream.
--
-- Thứ tự (priority cao chạy trước): openid-connect (2599) -> limit-count (1002) -> vobs-consent (999).
-- Nhờ vậy request vượt hạn mức bị chặn trước khi tốn một lần gọi service consent.
-- Plugin đọc client id của đối tác đã được xác thực: từ token (claim azp, hoặc client_id) khi route dùng
-- openid-connect với set_userinfo_header, hoặc từ tên consumer khi dùng plugin xác thực consumer như key-auth.
-- Sau đó lấy consent id từ header
-- x-consent-id và hỏi service consent qua API nội bộ
-- POST /internal/consent-checks (xem vobs-spec/openapi/internal-consent-check.yaml).
-- Mọi lỗi khi gọi service consent đều từ chối request (fail closed).
--
local core = require("apisix.core")
local http = require("resty.http")
local ngx = ngx

local plugin_name = "vobs-consent"

local schema = {
    type = "object",
    properties = {
        consent_service_uri = { type = "string", minLength = 1 },
        scope = {
            type = "string",
            enum = {
                "ACCOUNTS_READ", "TRANSACTIONS_READ",
                "PAYMENTS_INITIATE", "EWALLET_TOPUP", "EWALLET_WITHDRAWAL",
            },
        },
        -- Tên tham số trên đường dẫn route chứa mã tài khoản, ví dụ "account_id" cho /accounts/:account_id
        account_id_param = { type = "string" },
        timeout_ms = { type = "integer", minimum = 100, maximum = 10000, default = 2000 },
        ssl_verify = { type = "boolean", default = true },
    },
    required = { "consent_service_uri", "scope" },
}

local function runs_openid_connect(ctx)
    -- ctx.plugins: danh sách plugin của request, dạng { plugin, conf, plugin, conf, ... }
    local plugins = ctx.plugins
    if not plugins then
        return false
    end
    for i = 1, #plugins, 2 do
        if plugins[i].name == "openid-connect" then
            return true
        end
    end
    return false
end

local function claims_from_userinfo(ctx)
    local encoded = core.request.header(ctx, "X-Userinfo")
    if not encoded then
        return nil
    end
    local decoded = ngx.decode_base64(encoded)
    local claims = decoded and core.json.decode(decoded)
    return type(claims) == "table" and claims or nil
end

local function client_id_of(ctx)
    if ctx.vobs_client_id ~= nil then
        return ctx.vobs_client_id or nil
    end
    local client_id
    if runs_openid_connect(ctx) then
        -- openid-connect (APISIX 3.19) không đặt payload token vào ctx. Với set_userinfo_header = true, plugin xóa
        -- X-Userinfo do client tự gửi rồi đặt lại bằng payload (base64 JSON) của token đã xác thực. Chỉ tin header
        -- này khi route có openid-connect; route khác thì client tự gửi được X-Userinfo để giả danh.
        local claims = claims_from_userinfo(ctx)
        client_id = claims and (claims.azp or claims.client_id)
    else
        -- key-auth, jwt-auth... chỉ đặt ctx.consumer_name sau khi xác thực consumer thành công
        client_id = ctx.consumer_name
    end
    -- Ghi nhớ trong request: proxy-rewrite, limit-count, vobs-consent, kafka-logger đều đọc biến này
    ctx.vobs_client_id = client_id or false
    return client_id
end

-- $vobs_client_id: khóa giới hạn tần suất của limit-count và trường của log kiểm toán (kafka-logger).
-- Tính từ token nên dùng được cả ở plugin chạy trước vobs-consent.
core.ctx.register_var("vobs_client_id", client_id_of)
-- $vobs_consent_id: consent đã được kiểm tra, cho log kiểm toán
core.ctx.register_var("vobs_consent_id", function(ctx) return ctx.vobs_consent_id end)

local _M = {
    version = 0.1,
    priority = 999,
    name = plugin_name,
    schema = schema,
}

function _M.check_schema(conf)
    return core.schema.check(schema, conf)
end

local function deny(status, reason)
    return status, {
        type = "https://vobs.io/problems/consent-denied",
        title = "Consent không cho phép truy cập",
        status = status,
        reason = reason,
    }
end

local function account_id_of(conf, ctx)
    if not conf.account_id_param then
        return nil
    end
    local matched = ctx.curr_req_matched
    return matched and matched[conf.account_id_param]
end

function _M.access(conf, ctx)
    local consent_id = core.request.header(ctx, "x-consent-id")
    if not consent_id or consent_id == "" then
        return deny(403, "consent_required")
    end

    local client_id = client_id_of(ctx)
    if not client_id then
        core.log.error("vobs-consent: không có client id; cần một plugin xác thực (openid-connect, key-auth...) "
            .. "chạy trước plugin này")
        return deny(401, "client_unauthenticated")
    end

    local body, err = core.json.encode({
        consentId = consent_id,
        clientId = client_id,
        scope = conf.scope,
        accountId = account_id_of(conf, ctx),
    })
    if not body then
        core.log.error("vobs-consent: không mã hóa được request: ", err)
        return 500
    end

    local httpc = http.new()
    httpc:set_timeout(conf.timeout_ms)
    local res, req_err = httpc:request_uri(conf.consent_service_uri .. "/internal/consent-checks", {
        method = "POST",
        body = body,
        headers = { ["Content-Type"] = "application/json" },
        ssl_verify = conf.ssl_verify,
    })
    if not res then
        core.log.error("vobs-consent: không gọi được service consent: ", req_err)
        return 503, { reason = "consent_service_unavailable" }
    end
    if res.status ~= 200 then
        core.log.error("vobs-consent: service consent trả về HTTP ", res.status)
        return 503, { reason = "consent_service_unavailable" }
    end

    local decision = core.json.decode(res.body)
    if type(decision) ~= "table" or decision.allowed ~= true then
        return deny(403, type(decision) == "table" and decision.reason or "consent_denied")
    end

    ctx.vobs_consent_id = consent_id
end

return _M
