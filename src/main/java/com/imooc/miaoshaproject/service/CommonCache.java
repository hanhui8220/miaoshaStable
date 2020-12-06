package com.imooc.miaoshaproject.service;

public interface CommonCache {

    public void putCommonCache(String key,Object value);

    public Object getFromCommonCache(String key);
}
