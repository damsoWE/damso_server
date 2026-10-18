-- 메일 발송 한 건을 예약한다. 허용되면 0, 거부되면 기다려야 하는 밀리초를 반환한다.
-- KEYS[1] 키 / ARGV[1] now(ms) / ARGV[2] 최소 간격(ms) / ARGV[3] 윈도(ms) / ARGV[4] 윈도 안 최대 횟수 / ARGV[5] 고유 멤버
local key = KEYS[1]
local now = tonumber(ARGV[1])
local minGap = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local max = tonumber(ARGV[4])

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local last = redis.call('ZRANGE', key, -1, -1, 'WITHSCORES')
if #last > 0 then
  local sinceLast = now - tonumber(last[2])
  if sinceLast < minGap then
    return minGap - sinceLast
  end
end

if redis.call('ZCARD', key) >= max then
  local first = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
  return window - (now - tonumber(first[2]))
end

redis.call('ZADD', key, now, ARGV[5])
redis.call('PEXPIRE', key, window)
return 0
