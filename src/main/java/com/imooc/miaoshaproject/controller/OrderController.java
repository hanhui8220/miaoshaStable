package com.imooc.miaoshaproject.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.imooc.miaoshaproject.mq.MqProcuder;
import com.imooc.miaoshaproject.service.ItemService;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.response.CommonReturnType;
import com.imooc.miaoshaproject.service.PromoService;
import com.imooc.miaoshaproject.service.model.OrderModel;
import com.imooc.miaoshaproject.service.model.UserModel;
import com.imooc.miaoshaproject.util.CodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;


@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProcuder mqProcuder;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    // guava 自带的限流计数器
    private RateLimiter createOrderLimiter;


    // 使用 队列化 泄洪
    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(20);
        // 默认限制100个
        createOrderLimiter =  RateLimiter.create(100);
    }


    // 生成验证码
    @RequestMapping(value = "/generateVerifyCode",method = {RequestMethod.GET})
    @ResponseBody
    public void generateVerifyCode(HttpServletResponse response) throws IOException, BusinessException {
        //获取 token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能生成验证码");
        }
        // 从redis中获取 用户登录对象
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户信息已过期，请重新登录");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("item_verify_code_"+userModel.getId(),map.get("code"));
        redisTemplate.expire("item_verify_code_"+userModel.getId(),10,TimeUnit.MINUTES);
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
        System.out.println("验证码的值为："+map.get("code"));
    }

    @RequestMapping(value = "/generateProKillToken",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateProKillToken(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name="promoId")Integer promoId,
                                         @RequestParam(name="verifyCode")String verifyCode) throws BusinessException{

        //获取 token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }
        //  判断是否存在 库存已售罄 标识
        if(redisTemplate.hasKey("item_stock_sole_out" + itemId)){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        // 从redis中获取 用户登录对象
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户信息已过期，请重新登录");
        }
        // 判断 验证码
        if(StringUtils.isEmpty(verifyCode)){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"验证码不能为空");
        }
        String  redisVerfiCode = (String)redisTemplate.opsForValue().get("item_verify_code_" + userModel.getId());
        if(!redisVerfiCode.equalsIgnoreCase(verifyCode)){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"验证码输入错误");
        }

        // 判断令牌的数量 是否超过最大值
        long result = redisTemplate.opsForValue().increment("item_door_count_"+itemId,-1);
        if(result < 0){
            return  null;
        }

        String killToken = promoService.generateProKillToken(itemId, promoId, userModel.getId());
        if(killToken == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"生成秒杀令牌失败");
        }
        //返回对应的结果
        return CommonReturnType.create(killToken);
    }


    //封装下单请求
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name="amount")Integer amount,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                        @RequestParam(name="promoToken",required = false) String promoToken) throws BusinessException {
        //  限流，  判断 计数器是否可用
        if(!createOrderLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.RATE_LIMITER);
        }

        //获取 token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }
        // 从redis中获取 用户登录对象
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户信息已过期，请重新登录");
        }
//        Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
//        if(isLogin == null || !isLogin.booleanValue()){
//            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
//        }

        //获取用户的登陆信息
       //UserModel userModel = (UserModel)httpServletRequest.getSession().getAttribute("LOGIN_USER");

        //OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);


        if(promoId != null ){
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("item_pro_kill_token_"+promoId+"_"+itemId+"_"+userModel.getId());
            if(inRedisPromoToken == null){
                throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"秒杀令牌已过期");
            }
            if(!promoToken.equals(inRedisPromoToken)){
                throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"秒杀令牌校验失败");
            }
        }

        //  判断是否存在 库存已售罄 标识
        if(redisTemplate.hasKey("item_stock_sole_out" + itemId)){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        // 使用队列 泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                // 初始化  一个 交易流水
                String stockLogId = itemService.initStockLog(itemId, amount);

                //  等到 订单 完成后再  减库存
                if (!mqProcuder.transactionAsyncDecreaseStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return CommonReturnType.create(null);
    }
}
