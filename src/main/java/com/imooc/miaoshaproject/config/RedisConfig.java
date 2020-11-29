package com.imooc.miaoshaproject.config;

import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Component;

/**
 *          将session 放到Redis中的配置类
 *
 */
@Component
@EnableRedisHttpSession
public class RedisConfig {
}
