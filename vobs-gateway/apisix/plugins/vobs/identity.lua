--
-- Licensed under the Apache License, Version 2.0. See LICENSE in the repository root.
--
-- Danh tính của đối tác đã được xác thực trong request hiện tại, dùng chung cho các plugin vobs-*.
--
-- openid-connect (APISIX 3.19) không đặt payload token vào context của request. Với set_userinfo_header = true, plugin
-- này xóa header X-Userinfo do client tự gửi rồi đặt lại bằng payload (base64 JSON) của token đã xác thực. Vì vậy
-- X-Userinfo chỉ đáng tin khi route có openid-connect; route khác thì client tự gửi được header này để giả danh.
--
local core = require("apisix.core")
local ngx = ngx

local _M = {}

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

-- Payload của token đã được openid-connect xác thực, hoặc nil nếu route không dùng openid-connect.
function _M.claims(ctx)
    if ctx.vobs_claims ~= nil then
        return ctx.vobs_claims or nil
    end
    local claims
    if runs_openid_connect(ctx) then
        local encoded = core.request.header(ctx, "X-Userinfo")
        local decoded = encoded and ngx.decode_base64(encoded)
        local value = decoded and core.json.decode(decoded)
        claims = type(value) == "table" and value or nil
    end
    ctx.vobs_claims = claims or false
    return claims
end

-- Mã client của đối tác: claim azp (hoặc client_id) của token khi route dùng openid-connect; tên consumer khi route
-- dùng plugin xác thực consumer (key-auth, jwt-auth...), vì các plugin đó chỉ đặt ctx.consumer_name sau khi xác
-- thực thành công.
function _M.client_id(ctx)
    if runs_openid_connect(ctx) then
        local claims = _M.claims(ctx)
        return claims and (claims.azp or claims.client_id) or nil
    end
    return ctx.consumer_name
end

return _M
