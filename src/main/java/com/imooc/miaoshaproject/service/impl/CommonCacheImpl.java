package com.imooc.miaoshaproject.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.imooc.miaoshaproject.service.CommonCache;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
public class CommonCacheImpl implements CommonCache {


    private Cache<String,Object> cache = null;

    //在构造方法后  ，init 方法前   执行
    @PostConstruct
    public void init() {
        // 创建一个 缓存类，设置初始化大小为10，最大为100，且有效时间在 数据写入缓存后1分钟
        this.cache = CacheBuilder.newBuilder().initialCapacity(10)
        .maximumSize(100)
        .expireAfterWrite(1, TimeUnit.MINUTES).build();

    }

    @Override
    public void putCommonCache(String key, Object value) {
        cache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return cache.getIfPresent(key);
    }
}
