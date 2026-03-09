-- 秒杀补偿脚本：回滚库存和用户标记
-- 当订单创建失败时调用

-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.回滚操作
-- 3.1.恢复库存
redis.call('incrby', stockKey, 1)
-- 3.2.移除用户标记
redis.call('srem', orderKey, userId)

return 0  -- 成功
