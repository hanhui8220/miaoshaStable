package com.imooc.miaoshaproject.mq;


import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.dao.StockLogDOMapper;
import com.imooc.miaoshaproject.dataobject.StockLogDO;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.service.model.OrderModel;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProcuder {

    private DefaultMQProducer defaultMQProducer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.namerserver.addr}")
    private String mqAddr;

    @Value("${mq.topicname}")
    private String mqTopicName;

    @Autowired
    private OrderService orderService;

    @Resource
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
//        defaultMQProducer = new DefaultMQProducer("stock_procuder_group");
//        defaultMQProducer.setNamesrvAddr(mqAddr);
//        defaultMQProducer.start();

        transactionMQProducer = new TransactionMQProducer("stock_procuder_group");
        transactionMQProducer.setNamesrvAddr(mqAddr);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {

            // 真正要做的事
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object arg) {
                //真正要做的事  创建订单
                Integer itemId = (Integer) ((Map)arg).get("itemId");
                Integer promoId = (Integer) ((Map)arg).get("promoId");
                Integer userId = (Integer) ((Map)arg).get("userId");
                Integer amount = (Integer) ((Map)arg).get("amount");
                String stockLogId = (String) ((Map)arg).get("stockLogId");
                try {
                    orderService.createOrder(userId, itemId, promoId, amount,stockLogId);
                    System.out.println(" 订单 创建完毕---------------");
                } catch (BusinessException e) {
                    e.printStackTrace();
                    // 修改  StockLog  为 失败状态
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                String jsonString = new String(messageExt.getBody());
                Map<String,Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = Integer.valueOf(map.get("itemId").toString());
                Integer amount = Integer.valueOf(map.get("amount").toString());
                String stockLogId = (String) (map).get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO.getStatus().intValue() == 2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(stockLogDO.getStatus().intValue() == 1){
                    return LocalTransactionState.UNKNOW;
                }else{
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }
        });
    }

    //  事务型  发送消息
    public boolean transactionAsyncDecreaseStock(Integer userId, Integer itemId, Integer promoId, Integer amount,String stockLogId){
        Map<String,Object> map = new HashMap<>();
        map.put("itemId",itemId);
        map.put("amount",amount);
        map.put("stockLogId",stockLogId);

        Map<String,Object> argsMap = new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);

        Message message = new Message(mqTopicName,"increase",
                JSON.toJSON(map).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = null;
        try {
            System.out.println("开始 发送消息---------------------");
            sendResult = transactionMQProducer.sendMessageInTransaction(message,argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return  false;
        }
        System.out.println("mq 发送事务消息完毕------------------------");
        //如果返回状态 等于回滚,表示消息 发送不成功
        if(sendResult.getLocalTransactionState().equals(LocalTransactionState.ROLLBACK_MESSAGE)){
            return false;
        }else if(sendResult.getLocalTransactionState().equals(LocalTransactionState.COMMIT_MESSAGE)){
            return true;
        }else{
            return false;
        }
    }

    public boolean asyncDecreaseStock(Integer itemId,Integer amount){
        Map<String,Object> map = new HashMap<>();
        map.put("itemId",itemId);
        map.put("amount",amount);
        Message message = new Message(mqTopicName,"increase",
                JSON.toJSON(map).toString().getBytes(Charset.forName("UTF-8")));
        try {
            SendResult sendResult = defaultMQProducer.send(message);
            System.out.println("mq 发送消息完毕------------------------");
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


}
