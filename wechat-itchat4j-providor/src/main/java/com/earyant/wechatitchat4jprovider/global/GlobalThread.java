package com.earyant.wechatitchat4jprovider.global;

import com.alibaba.fastjson.JSON;
import com.earyant.wechatitchat4jprovider.dao.User;
import com.earyant.wechatitchat4jprovider.itchat4j.service.impl.LoginServiceImpl;
import com.earyant.wechatitchat4jprovider.itchat4j.thread.CheckLoginStatusThread;
import com.earyant.wechatitchat4jprovider.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Created by earyant on 2017 : 08 : 2017/8/8 : 15:24 : .
 * ln_spring_boot  com.earyant.wechatitchat4jprovider.global
 */

@Component
@EnableScheduling
public class GlobalThread {
    Logger LOG = Logger.getLogger(GlobalThread.class.getName());
    ExecutorService service = Executors.newFixedThreadPool(10);
    @Autowired
    LoginServiceImpl loginService;
    @Autowired
    StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 6000000)
    public void run() {
        LOG.info("启动成功，正在检测状态");
        while (true) {
            Set<String> keys = redisTemplate.keys("user*");
            keys.forEach(key -> {
                User user = null;
                String us = redisTemplate.opsForValue().get(key);
                if (!StringUtils.isEmpty(us)) {
                    try {
                        user = JSON.parseObject(us, User.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (user != null && !user.isAlive() && user.getLoginRetryCount() > 0 && loginService.login(user)) {
                        LOG.info("5.login success , wechat Init");
                        if (!loginService.webWxInit(user.getWechatId())) {
                            LOG.info("6. wechat init excption");
                        }
                        LOG.info("6. start wechat notify");
                        loginService.wxStatusNotify(user.getWechatId());
                        LOG.info(String.format("welcome back， %s", user.getNickName()));
                        LOG.info("8. start receive message ");
                        loginService.startReceiving(user.getWechatId());
                        LOG.info("9. get contact message");
                        loginService.webWxGetContact(user.getWechatId());
                        LOG.info("10. get group and group list");
                        loginService.WebWxBatchGetContact(user.getWechatId());
//                    LOG.info("11. cache friend message");
//                    WechatTools.setUserInfo(); // 登陆成功后缓存本次登陆好友相关消息（NickName, UserName）
                        LOG.info("12.open wechat status tread");
                        new Thread(new CheckLoginStatusThread()).start();
                    } else {
                        if (user.getLoginRetryCount() > 0) {
//                            redisTemplate.opsForValue().set(key, JSON.toJSONString(user));
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
