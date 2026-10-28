-- 요청 한 건을 여러 칸에 한꺼번에 예약한다(비밀번호 재설정 메일). 모든 칸이 허용하면 모든 칸에 기록하고 0을 반환한다.
-- 하나라도 거부하면 어느 칸에도 기록하지 않고, 거부한 칸들의 대기 밀리초 중 가장 큰 값을 반환한다
-- (거부된 요청이 다른 칸을 소모하지 않게, 이슈 #26).
-- KEYS[1..n] 칸 키 / ARGV[1] now(ms) / ARGV[2] 고유 멤버
-- ARGV[3 + 3(i-1)], ARGV[4 + 3(i-1)], ARGV[5 + 3(i-1)] 칸 i의 최소 간격(ms), 윈도(ms), 윈도 안 최대 횟수
local now = tonumber(ARGV[1])
local member = ARGV[2]

local function limitOf(i)
  local base = 3 + (i - 1) * 3
  return tonumber(ARGV[base]), tonumber(ARGV[base + 1]), tonumber(ARGV[base + 2])
end

local maxWait = 0
for i, key in ipairs(KEYS) do
  local minGap, window, max = limitOf(i)
  redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

  local last = redis.call('ZRANGE', key, -1, -1, 'WITHSCORES')
  if #last > 0 then
    local sinceLast = now - tonumber(last[2])
    if sinceLast < minGap then
      maxWait = math.max(maxWait, minGap - sinceLast)
    end
  end

  if redis.call('ZCARD', key) >= max then
    local first = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    maxWait = math.max(maxWait, window - (now - tonumber(first[2])))
  end
end

if maxWait > 0 then
  return maxWait
end

for i, key in ipairs(KEYS) do
  local _, window = limitOf(i)
  redis.call('ZADD', key, now, member)
  redis.call('PEXPIRE', key, window)
end
return 0
