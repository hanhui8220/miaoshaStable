package com.imooc.miaoshaproject.service.impl;

import com.imooc.miaoshaproject.dao.PromoDOMapper;
import com.imooc.miaoshaproject.dataobject.PromoDO;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.service.ItemService;
import com.imooc.miaoshaproject.service.PromoService;
import com.imooc.miaoshaproject.service.UserService;
import com.imooc.miaoshaproject.service.model.ItemModel;
import com.imooc.miaoshaproject.service.model.PromoModel;
import com.imooc.miaoshaproject.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    // 商品  发布  将商品信息 存放在redis中
    @Override
    public void publishPromo(Integer promoId) {
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO == null || promoDO.getItemId().intValue() == 0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        redisTemplate.opsForValue().set("item_stock_"+itemModel.getId(),itemModel.getStock());
        // 限制 令牌的量 为 库存的数量 * 5
        redisTemplate.opsForValue().set("item_door_count_"+itemModel.getId(),itemModel.getStock()*5);
    }

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }
    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }


    //  生成秒杀令牌
    @Override
    public String generateProKillToken(Integer itemId, Integer promoId, Integer userId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        // 如果当前 活动没有进行，返回null
        if(promoModel.getStatus().intValue() != 2) {
            return null;
        }
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }

        // 从缓存获取
        //UserModel userModel = userService.getUserById(userId);
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }
        String promoToken = UUID.randomUUID().toString().replace("-","");
        //  将 token  放在 redis中，  一个秒杀活动一个用户 只能对一个商品  生成令牌
        redisTemplate.opsForValue().set("item_pro_kill_token_"+promoId+"_"+itemId+"_"+userId,promoToken);
        redisTemplate.expire("item_pro_kill_token_"+promoId+"_"+itemId+"_"+userId,5, TimeUnit.MINUTES);
        return promoToken;

    }
}
