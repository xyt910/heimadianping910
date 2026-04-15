package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

//解决缓存穿透的做法
    //缓存穿透
    //cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

//    @Override
//    public Result queryById(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3. 存在，返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//
//        // 判断命中的是否为空值
//        if(shopJson != null){
//            return Result.fail("店铺不存在!");
//        }
//
//        //4. 不存在，根据id查询数据库
//        Shop shop = getById(id);
//
//        //5.不存在，返回错误
//        if(shop == null){
//            //将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            //返回错误信息
//            return Result.fail("店铺不存在!");
//        }
//
//        //6. 存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        if(shop == null){
//            return Result.fail("店铺不存在!");
//        }
//        //7. 返回
//        return Result.ok(shop);
//    }

//解决缓存击穿的做法（互斥锁）
//    @Override
//    public Result queryById(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3. 存在，返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//
//        // 判断命中的是否为空值
//        if(shopJson != null){
//            return Result.fail("店铺不存在!");
//        }
//
//        //4. 不存在，实现缓存重建
//        // 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        Shop shop = null;
//
//        try {
//            // 判断是否获取锁成功
//            if(!isLock){
//                //失败，则休眠并重试
//                Thread.sleep(50);
//                return queryById(id);
//            }
//
//            // 成功则再次判断缓存是否存在
//            if(StrUtil.isNotBlank(shopJson)){
//                //3. 存在，返回
//                shop = JSONUtil.toBean(shopJson, Shop.class);
//                return Result.ok(shop);
//            }
//
//            // 判断命中的是否为空值
//            if(shopJson != null){
//                return Result.fail("店铺不存在!");
//            }
//
//            //不存在，根据id查询数据库
//            shop = getById(id);
//
//            // 模拟重建的延时
//            Thread.sleep(200);
//
//            //5.不存在，返回错误
//            if(shop == null){
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//                //返回错误信息
//                return Result.fail("店铺不存在!");
//            }
//
//            //6. 存在，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放互斥锁
//            unLock(lockKey);
//        }
//
//        if(shop == null){
//            return Result.fail("店铺不存在!");
//        }
//
//        //7. 返回
//        return Result.ok(shop);
//    }

//    //解决缓存击穿的做法（设置逻辑过期时间）
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        //1. 从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2. 判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //3. 不存在，返回
//            return null;
//        }
//
//        // 命中，将数据从redis反序列化成对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        // 判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            // 未过期，直接返回
//            return Result.ok(shop);
//        }
//
//        // 过期，尝试获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//
//        //判断获取锁是否成功
//        if(isLock){
//            //  获取锁成功，再次判断redis缓存是否过期
//            //  还是过期则开启独立线程，实现缓存重建
//            if(!expireTime.isAfter(LocalDateTime.now())){
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    try {
//                        // 重建缓存
//                        this.saveShop2Redis(id, 20L);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        // 释放锁
//                        unLock(lockKey);
//                    }
//                });
//
//            }
//            //  未过期则无需重建缓存，直接返回
//            return Result.ok(shop);
//        }
//
        if(shop == null){
            return Result.fail("店铺不存在!");
        }

        // 返回过期商铺信息
        return Result.ok(shop);
    }
//
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        // 1. 查询店铺数据
//        Shop shop = getById(id);
//
//        Thread.sleep(200);
//
//        // 2. 封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//
//        // 3. 写入redis
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
