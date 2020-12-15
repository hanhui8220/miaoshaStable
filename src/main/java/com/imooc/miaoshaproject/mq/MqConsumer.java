package com.imooc.miaoshaproject.mq;


import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class MqConsumer {

    @Value("mq.namerserver.addr")
    private String mqAddr;

    @Value("mq.topicname")
    private String mqTopicName;

    private DefaultMQPushConsumer defaultMQPushConsumer;

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
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
    }



}
