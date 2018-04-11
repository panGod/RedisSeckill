package com.pan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.Random;

@RestController
public class SecKillController {



    @RequestMapping("/seckill/{prodid}")
    public String seckill(@PathVariable("prodid") String prodid){
        String userid = new Random().nextInt(50000)+"";

        boolean result = doSeckillByScript(userid, prodid);
        if (result){

            return "success";
        }

        return  "false";

    }

    /**
     * 出现超卖问题
     * @param userid
     * @param prodid
     * @return
     */
    public static boolean doSeckill(String userid, String prodid){
        String qtKey = "sk:"+prodid+":qt";
        String usrKey = "sk:"+prodid+":usr";
        JedisPool jedisPool = JedisPoolUtil.getJedisPool();
        Jedis jedis = jedisPool.getResource();
        System.out.println("active:"+jedisPool.getNumActive()+",waiter:"+jedisPool.getNumWaiters());
        //判断是否该用户是否已经抢到了
        if (jedis.sismember(usrKey,userid)){
            System.err.println(userid+"已经抢到了，不能重复抢！");
            jedis.close();
            return false;
        }
        //先判断库存中是否有商品
        String qt = jedis.get(qtKey);
        if (qt == null){

            System.out.println("未初始化！！");
            jedis.close();
            return  false;
        }else {
            if (Integer.parseInt(qt) <= 0){
                System.err.println("已抢空！！！！");
                jedis.close();
                return  false;
            }
        }
        //减库存
        jedis.decr(qtKey);
        //加人
        jedis.sadd(usrKey,userid);
        jedis.close();
        System.out.println("恭喜用户"+userid+"已经秒杀成功！！");
        return  true;
    }

    /**
     * 使用redis乐观锁，出现剩余问题
     * @param uid
     * @param prodid
     * @return
     */
    public static boolean doSeckillByWatch(String uid, String prodid){
        JedisPool jedisPool = JedisPoolUtil.getJedisPool();
        Jedis jedis= jedisPool.getResource();
        System.out.println("active:" + jedisPool.getNumActive() + "||" + "wait:" + jedisPool.getNumWaiters());
        String qtkey="sk:"+prodid+":qt";
        String usrkey="sk:"+prodid+":usr";
        //判断是否已经抢过
        if( jedis.sismember(usrkey, uid)){
            jedis.close();
            System.err.println("已抢过！！！！！");
            return false;
        }
        //判断是否还有库存
        jedis.watch(qtkey);
        int qt= Integer.parseInt(jedis.get(qtkey)) ;
        if (qt<=0){
            jedis.close();
            System.err.println("没有库存！！！！！！！！");
            return false;
        }
        Transaction transaction= jedis.multi();
        //减库存
        transaction.decr(qtkey);
        //加人
        transaction.sadd(usrkey, uid );
        List<Object> list = transaction.exec();
        if(list==null||list.size()==0){
            System.err.println("抢购失败！！！！！！！！！！");
            jedis.close();
            return false;
        }
        jedis.close();
        System.out.println("抢购成功");
        return true;
    }


    /**
     * 使用Lua脚本进行处理秒杀，因为redis是单线程的，
     * 所以我们使用Lua脚本嵌入式到redis里面进行，当成一个业务进行处理，
     * 将并行转换为串行行为，那么这样就不会受并发影响
     * 而且还能减少连接redis次数
     * @param uid
     * @param prodid
     * @return
     */
    public static boolean doSeckillByScript(String uid, String prodid){
        JedisPool jedisPool = JedisPoolUtil.getJedisPool();
        Jedis jedis = jedisPool.getResource();
        String seckillScript = "local userid=KEYS[1];\n" +
                "local prodid=KEYS[2];\n" +
                "local qtKey=\"sk:\"..prodid..\":qt\";\n" +
                "local userKey=\"sk:\"..prodid..\":usr\";\n" +
                "local userExist=redis.call(\"sismember\",userKey,userid);\n" +
                "if(tonumber(userExist)==1) then\n" +
                "\treturn 2;\n" +
                "end\n" +
                "local qtNum=redis.call(\"get\",qtKey)\n" +
                "if(tonumber(qtNum)<=0) then\n" +
                "\treturn 0;\n" +
                "else\n" +
                "\tredis.call(\"decr\",qtKey);\n" +
                "\tredis.call(\"sadd\",userKey,userid);\n" +
                "end\n" +
                "return 1;\n" +
                "\n" +
                "\n";
        Object eval = jedis.eval(seckillScript,2, uid, prodid);
        String result = String.valueOf(eval);
        if (result.equals("1")){
            System.out.println("用户："+uid+"抢购成功！");
            return  true;
        }else if (result.equals("0")){
            System.err.println("商品库存不足！");
            return  false;
        }else if(result.equals("2")){
            System.err.println("用户："+uid+"已经抢购过该商品，不可重复抢购！");
            return  false;
        }
        return  false;
    }





}
