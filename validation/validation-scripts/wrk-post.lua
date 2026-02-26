-- wrk script for YAWL HTTP performance testing
-- This script handles POST requests and measures response times

local function response()
    -- Measure response time in milliseconds
    local response_time = tonumber(string.match(wrk.headers["x-response-time"], "(%d+)"))
    if not response_time then
        response_time = tonumber(wrk.formatDuration(wrk.response.headers["x-response-time"]):match("(%d+)")) or 0
    end

    -- Update response time in seconds for wrk statistics
    return wrk.format_response(nil, nil, response_time)
end

-- Set headers
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Accept"] = "application/json"

-- Prepare request body
local request_body = '{"workflowId":"performance-test","data":{"test":true}}'

-- Setup function called once at startup
function setup(thread)
    thread:set("request_body", request_body)
end

-- Request function called for each request
function request()
    return wrk.format('POST', '/api/workflow', nil, wrk.thread:get("request_body"))
end