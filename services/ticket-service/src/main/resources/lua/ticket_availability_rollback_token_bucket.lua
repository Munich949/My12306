-- 处理inputString得到actualKey
local inputString = KEYS[2]
local actualKey = inputString
-- 找到 ":" 的索引位置
local colonIndex = string.find(actualKey, ":")
-- 如果在 Key 中确实找到了 ":" 则将actualKey设置为冒号之后的字符串部分
if colonIndex ~= nil then
    actualKey = string.sub(actualKey, colonIndex + 1)
end

-- 解析JSON字符串为Lua表
local jsonArrayStr = ARGV[1]
local jsonArray = cjson.decode(jsonArrayStr)
local alongJsonArrayStr = ARGV[2]
local alongJsonArray = cjson.decode(alongJsonArrayStr)

-- 遍历和操作
for index, jsonObj in ipairs(jsonArray) do
    -- 对jsonArray进行遍历，每轮循环处理一个 jsonObj。从中获取座位类型和座位数量。
    local seatType = tonumber(jsonObj.seatType)
    local count = tonumber(jsonObj.count)
    for indexTwo, alongJsonObj in ipairs(alongJsonArray) do
        -- 对alongJsonArray进行遍历，每次处理一个 alongJsonObj。从中获取始发站和终点站。
        local startStation = tostring(alongJsonObj.startStation)
        local endStation = tostring(alongJsonObj.endStation)
        -- 使用始发站、终点站和座位类型构造一个哈希键，格式为：“始发站_终点站_座位类型”。
        local actualInnerHashKey = startStation .. "_" .. endStation .. "_" .. seatType
        -- 获取剩余座位数量
        local ticketSeatAvailabilityTokenValue = tonumber(redis.call('hget', KEYS[1], tostring(actualInnerHashKey)))
        if ticketSeatAvailabilityTokenValue >= 0 then
            redis.call('hincrby', KEYS[1], tostring(actualInnerHashKey), count)
        end
    end
end

return 0