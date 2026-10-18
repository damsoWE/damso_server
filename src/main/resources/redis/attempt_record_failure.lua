-- 연속 실패 횟수를 올리고 규칙에 따른 대기 종료 시각을 정한다 (원자 처리).
-- KEYS[1] 키 / ARGV[1] now(ms) / ARGV[2] restartAfterLock(1|0) / ARGV[3] ttl(초) / ARGV[4..] 횟수별 대기(초)
-- 반환: { 실패 횟수, 대기 종료 시각(ms, 0이면 없음) }
local key = KEYS[1]
local now = tonumber(ARGV[1])
local restart = ARGV[2] == '1'
local ttl = tonumber(ARGV[3])
local waits = {}
for i = 4, #ARGV do
  waits[#waits + 1] = tonumber(ARGV[i])
end

local count = tonumber(redis.call('HGET', key, 'count') or '0')
local untilMs = tonumber(redis.call('HGET', key, 'until') or '0')

-- 대기·잠금 중의 시도는 횟수에 더하지 않는다
if untilMs > now then
  return { count, untilMs }
end
-- 잠금이 풀린 뒤에는 0회부터 다시 센다(코드 입력)
if restart and untilMs > 0 then
  count = 0
end

count = count + 1
local wait = waits[math.min(count, #waits)]
local newUntil = 0
if wait > 0 then
  newUntil = now + wait * 1000
end
redis.call('HSET', key, 'count', count, 'until', newUntil)
redis.call('EXPIRE', key, ttl)
return { count, newUntil }
