package com.imooc.miaoshaproject.mq;


import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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



    @PostConstruct
    public void init() throws MQClientException {
        defaultMQProducer = new DefaultMQProducer("stock_procuder_group");
        defaultMQProducer.setNamesrvAddr(mqAddr);
        defaultMQProducer.start();

        transactionMQProducer = new TransactionMQProducer("stock_procuder_group");
        transactionMQProducer.setNamesrvAddr(mqAddr);
        transactionMQProducer.start();
    }

    //  事务型  发送消息
    public boolean transactionAsyncDecreaseStock(Integer itemId,Integer amount){
        Map<String,Object> map = new HashMap<>();
        map.put("itemId",itemId);
        map.put("amount",amount);
        Message message = new Message(mqTopicName,"increase",
                JSON.toJSON(map).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = transactionMQProducer.sendMessageInTransaction(message);


        System.out.println("mq 发送消息完毕------------------------");

        return true;
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
