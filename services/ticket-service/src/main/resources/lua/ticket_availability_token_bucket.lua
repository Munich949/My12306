-- KEYS[2] 就是咱们用户购买的出发站点和到达站点，比如北京南_南京南
local inputString = KEYS[2]
local actualKey = inputString
-- 因为 Redis Key 序列化器的问题，会把 unique_name: 给加上
-- 所以这里需要把 unique_name: 给删除，仅保留北京南_南京南
-- actualKey 就是北京南_南京南
local colonIndex = string.find(actualKey, ":")
if colonIndex ~= nil then
    actualKey = string.sub(actualKey, colonIndex + 1)
end

-- ARGV[1] 是需要扣减的座位类型以及对应数量
local jsonArrayStr = ARGV[1]
-- 因为传递过来是 JSON 字符串，所以这里再序列化成对象
local jsonArray = cjson.decode(jsonArrayStr)

-- 返回的 JSON 对象字符串
local result = {}
-- 返回 JSON 对象字符串中的是否请求成功字段
local tokenIsNull = false
-- 如果请求失败，返回 JSON 对象字符串中的请求失败座位类型和数量
local tokenIsNullSeatTypeCounts = {}

-- 简单的 for 循环
for index, jsonObj in ipairs(jsonArray) do
    local seatType = tonumber(jsonObj.seatType)
    local count = tonumber(jsonObj.count)
    local actualInnerHashKey = actualKey .. "_" .. seatType
    -- 判断指定座位 Token 余量是否超过购买人数
    local ticketSeatAvailabilityTokenValue = tonumber(redis.call('hget', KEYS[1], tostring(actualInnerHashKey)))
    -- 如果超过那么设置 TOKEN 获取失败，以及记录失败座位类型和获取数量
    if ticketSeatAvailabilityTokenValue < count then
        tokenIsNull = true
        table.insert(tokenIsNullSeatTypeCounts, seatType .. "_" .. count)
    end
end

result['tokenIsNull'] = tokenIsNull
-- 如果令牌不足则直接返回失败
if tokenIsNull then
    result['tokenIsNullSeatTypeCounts'] = tokenIsNullSeatTypeCounts
    -- 序列化成 JSON 字符串
    return cjson.encode(result)
end

-- 通过上面的判断，已经知道令牌容器中对应的出发站点和到达站点对应的座位类型余票充足，开始进行扣减
local alongJsonArrayStr = ARGV[2]
-- 因为传递过来是 JSON 字符串，所以这里再序列化成对象
local alongJsonArray = cjson.decode(alongJsonArrayStr)

-- 双层 for 循环
for index, jsonObj in ipairs(jsonArray) do
    local seatType = tonumber(jsonObj.seatType)
    local count = tonumber(jsonObj.count)
    for indexTwo, alongJsonObj in ipairs(alongJsonArray) do
        local startStation = tostring(alongJsonObj.startStation)
        local endStation = tostring(alongJsonObj.endStation)
        -- 开始扣减出发站点和到达站点相关的车站令牌余量
        local actualInnerHashKey = startStation .. "_" .. endStation .. "_" .. seatType
        -- 扣减命令通过 hash 结构的自增命令 hincrby，因为是要扣减，所以 count 前面加了个负号
        redis.call('hincrby', KEYS[1], tostring(actualInnerHashKey), -count)
    end
end

-- 全部扣减完成没有异常
return cjson.encode(result)