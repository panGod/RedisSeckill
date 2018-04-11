package com.pan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import java.net.URL;


@Component
public class JedisPoolUtil {

    @Autowired
    private Parameters parameters;

    private static volatile JedisPool jedisPool = null;

    private JedisPoolUtil() {
    }

    @PostConstruct
    public  JedisPool setJedisPoolInstance() {


        if (null == jedisPool) {
            synchronized (JedisPoolUtil.class) {
                if (null == jedisPool) {
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    //设置最大连接
                    poolConfig.setMaxTotal(200);
                    //设置最大闲置
                    poolConfig.setMaxIdle(32);
                    poolConfig.setMaxWaitMillis(100*1000);
                    poolConfig.setBlockWhenExhausted(true);
                    poolConfig.setTestOnBorrow(true);

                    jedisPool = new JedisPool(poolConfig, parameters.getHost(), Integer.valueOf(parameters.getPort()), 60000,parameters.getPassword() );

                }
            }
        }
        return jedisPool;
    }

    public static JedisPool getJedisPool(){
        return  jedisPool;
    }






}
