package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        //1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2. 判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3. 存在，返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否为空值
        if(json != null){
            return null;
        }

        //4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //5.不存在，返回错误
        if(r == null){
            //将空值写入redis
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            //返回错误信息
            return null;
        }

        //6. 存在，写入redis
        this.set(key, r, time, unit);

        //7. 返回
        return r;
    }

    //解决缓存击穿的做法（设置逻辑过期时间）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;
        //1. 从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);

        //2. 判断是否存在
        if(StrUtil.isBlank(Json)){
            //3. 不存在，返回
            return null;
        }

        // 命中，将数据从redis反序列化成对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期，直接返回
            return r;
        }

        // 过期，尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        //判断获取锁是否成功
        if(isLock){
            //  获取锁成功，再次判断redis缓存是否过期
            //  还是过期则开启独立线程，实现缓存重建
            if(!expireTime.isAfter(LocalDateTime.now())){
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        //查询数据库
                        R r1 = dbFallback.apply(id);

                        // 写入redis
                        this.setWithLogicalExpire(key, r1, time, unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unLock(lockKey);
                    }
                });

            }
            //  未过期则无需重建缓存，直接返回
            return r;
        }

        // 返回过期商铺信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}
