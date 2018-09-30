/*
 * Copyright (c) 2018
 * User:qinghua.xu
 * File:RedisStorageService.java
 * Date:2018/09/28
 */

package me.xqh.awesome.delayqueue.storage.redis;

import com.alibaba.fastjson.JSON;
import me.xqh.awesome.delayqueue.common.AwesomeURL;
import me.xqh.awesome.delayqueue.common.Constants;
import me.xqh.awesome.delayqueue.common.StringUtils;
import me.xqh.awesome.delayqueue.common.exception.AwesomeException;
import me.xqh.awesome.delayqueue.common.exception.JobAlreadyExistException;
import me.xqh.awesome.delayqueue.storage.api.AbstractStorageService;
import me.xqh.awesome.delayqueue.storage.api.AwesomeJob;
import me.xqh.awesome.delayqueue.storage.api.AwesomeTopic;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 使用redis实现job的存储及获取
 * @author qinghua.xu
 * @date 2018/9/27
 *  TODO 并发处理
 **/
public class RedisStorageService extends AbstractStorageService {
    Logger logger = LoggerFactory.getLogger(RedisStorageService.class);
    private final JedisPool jedisPool;
    public RedisStorageService(AwesomeURL url){
        super(url);
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setTestOnBorrow(url.getParameter("test.on.borrow", true));
        config.setTestOnReturn(url.getParameter("test.on.return", false));
        config.setTestWhileIdle(url.getParameter("test.while.idle", false));
        if (url.getParameter("max.idle", 0) > 0) {
            config.setMaxIdle(url.getParameter("max.idle", 0));
        }
        if (url.getParameter("min.idle", 0) > 0) {
            config.setMinIdle(url.getParameter("min.idle", 0));
        }
        if (url.getParameter("max.active", 0) > 0) {
            config.setMaxTotal(url.getParameter("max.active", 0));
        }
        if (url.getParameter("max.total", 0) > 0) {
            config.setMaxTotal(url.getParameter("max.total", 0));
        }
        if (url.getParameter("max.wait", url.getParameter("timeout", 0)) > 0) {
            config.setMaxWaitMillis(url.getParameter("max.wait", url.getParameter("timeout", 0)));
        }
        if (url.getParameter("num.tests.per.eviction.run", 0) > 0) {
            config.setNumTestsPerEvictionRun(url.getParameter("num.tests.per.eviction.run", 0));
        }
        if (url.getParameter("time.between.eviction.runs.millis", 0) > 0) {
            config.setTimeBetweenEvictionRunsMillis(url.getParameter("time.between.eviction.runs.millis", 0));
        }
        if (url.getParameter("min.evictable.idle.time.millis", 0) > 0) {
            config.setMinEvictableIdleTimeMillis(url.getParameter("min.evictable.idle.time.millis", 0));
        }
        this.jedisPool = new JedisPool(config, url.getHost(), url.getPort(),
                url.getParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT), StringUtils.isEmpty(url.getPassword()) ? null : url.getPassword(),
                url.getParameter("db.index", 0));
    }

    @Override
    protected boolean doAddJob(AwesomeJob job) {
        job.setStatus(RedisConstants.JobStatus.delay.getValue());
        String jobId = job.getId();
        //TODO 改为lua script
        boolean result = false;
        try(Jedis jedis = jedisPool.getResource()) {
            Transaction tx = jedis.multi();
            tx.set(RedisConstants.generateJobKey(jobId), JSON.toJSONString(job));
            switch (job.getTriggerType()){
                case RedisConstants.triggerType_expire:
                    tx.zadd(RedisConstants.generateDelayBucketKey(),job.getExpireTime(),jobId);
                    break;
                case RedisConstants.triggerType_count:
                    break;
                case RedisConstants.triggerType_all:
                    tx.zadd(RedisConstants.generateDelayBucketKey(),job.getExpireTime(),jobId);
                    break;
                default:break;
            }
            tx.exec();
            System.out.println("job 加入：" + jobId+"; "+new Date(System.currentTimeMillis()));
            result = true;
        }catch (Exception e){
            System.out.println(e);
        }
        return result;
    }

    @Override
    protected boolean checkJobRestrict(AwesomeJob job) throws AwesomeException {
        //TODO 做topic、job的校验
//        try (Jedis jedis = jedisPool.getResource()){
//            String json = jedis.get(RedisConstants.generateJobKey(job.getId()));
//            AwesomeJob existJob =  JSON.parseObject(json,AwesomeJob.class);
//            if (null != existJob){
//                throw new JobAlreadyExistException();
//            }
//        }

        return true;
    }

    private void handleCountDown(Jedis jedis){
//        jedis.zadd(RedisConstants.generateCountBucketKey(),job.getExpireTime(),jobId);
    }
    @Override
    public AwesomeJob getJob(String id) {
        try (Jedis jedis = jedisPool.getResource()){
            String json = jedis.get(RedisConstants.generateJobKey(id));
            AwesomeJob job = JSON.parseObject(json,AwesomeJob.class);
            return job;
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public void removeJob(String id) {

    }

    @Override
    public List<AwesomeJob> listExpiredJobs(long currentTime) {
        try (Jedis jedis = jedisPool.getResource()){
            Set<String> jobSet = jedis.zrangeByScore(RedisConstants.generateDelayBucketKey(),0,currentTime);
            List<AwesomeJob> expiredList = new ArrayList<>(jobSet.size());
            for (String id: jobSet){
                String json = jedis.get(RedisConstants.generateJobKey(id));
                if (RedisConstants.isEmpty(json)){
                    break;
                }
                AwesomeJob job = JSON.parseObject(json,AwesomeJob.class);
                if (job.getStatus() == RedisConstants.JobStatus.delay.getValue()){
                    expiredList.add(job);
                }
            }
            return expiredList;
        }
    }

    @Override
    public void transferExpiredJobs(List<AwesomeJob> jobList) {
        try (Jedis jedis = jedisPool.getResource()){
            for (AwesomeJob job: jobList){
                job.setStatus(RedisConstants.JobStatus.ready.getValue());
                Transaction  tx = jedis.multi();
                try {
                    tx.set(RedisConstants.generateJobKey(job.getId()),JSON.toJSONString(job));
                    tx.zrem(RedisConstants.generateDelayBucketKey(),job.getId());
                    tx.zadd(RedisConstants.generateReadySetKey(job.getTopic()),job.getExpireTime(),job.getId());
                    tx.exec();
                }catch (Exception e){
                    tx.discard();
                    logger.error("job过期，转移到 ready queue消费出错，jobId:{},error:{}",job.getId(),e);
                }
            }
        }
    }

    @Override
    public List<AwesomeJob> consumeReadyJobs(String topic,int number) {
        try (Jedis jedis = jedisPool.getResource()){
            String topicListKey= RedisConstants.generateReadySetKey(topic);
            long realLength = Math.min(number,jedis.zcount(topicListKey,0,System.currentTimeMillis()));
            List<AwesomeJob> list = new ArrayList<>(Long.valueOf(realLength).intValue());
            Set<String> keys = jedis.zrange(topicListKey,0,realLength);
            for (String key:keys){
                try {
                    String json = jedis.get(RedisConstants.generateJobKey(key));
                    AwesomeJob job = JSON.parseObject(json,AwesomeJob.class);
                    job.setStatus(RedisConstants.JobStatus.reserved.getValue());
                    jedis.set(RedisConstants.generateJobKey(key), JSON.toJSONString(job));
                    jedis.zrem(topicListKey,key);
                    //TODO 处理unAck 消息
                    list.add(job);
                }catch (Exception e){
                    logger.error("ready queue消费出错，jobId:{},error:{}",key,e);
                }
            }

            return list;
        }
    }

    @Override
    public boolean addAwesomeTopic(AwesomeTopic topic) {
        try (Jedis jedis = jedisPool.getResource()){
            long result = jedis.setnx(RedisConstants.generateTopicKey(topic.getTopic()), JSON.toJSONString(topic));
            if (result == 0){
                return false;
            }
        }
        return true;
    }

    @Override
    public AwesomeTopic getAwesomeTopic(String topic) {
        try (Jedis jedis = jedisPool.getResource()){
            String json = jedis.get(RedisConstants.generateTopicKey(topic));
            return JSON.parseObject(json,AwesomeTopic.class);
        }
    }
}
