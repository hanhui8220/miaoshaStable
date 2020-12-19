package com.imooc.miaoshaproject.mq;


import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.dao.ItemStockDOMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Component
public class MqConsumer {

    @Value("${mq.namerserver.addr}")
    private String mqAddr;

    @Value("${mq.topicname}")
    private String mqTopicName;

    private DefaultMQPushConsumer defaultMQPushConsumer;

    @Resource
    private ItemStockDOMapper itemStockDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        defaultMQPushConsumer = new DefaultMQPushConsumer("stock_consumer_group");
        defaultMQPushConsumer.setNamesrvAddr(mqAddr);
        defaultMQPushConsumer.subscribe(mqTopicName,"*");

        defaultMQPushConsumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                System.out.println("  收到消息---------->"+ list);
                System.out.println("  consumeConcurrentlyContext---------->"+ consumeConcurrentlyContext);
                Message messageExt = list.get(0);
                String jsonString = new String(messageExt.getBody());
                Map<String,Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = Integer.valueOf(map.get("itemId").toString());
                Integer amount = Integer.valueOf(map.get("amount").toString());
                itemStockDOMapper.decreaseStock(itemId,amount);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        defaultMQPushConsumer.start();
    }



}
