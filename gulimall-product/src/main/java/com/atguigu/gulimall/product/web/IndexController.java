package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
public class IndexController {

    @Autowired
    CategoryService categoryService;

    @Autowired
    RedissonClient redisson;

    @Autowired
    StringRedisTemplate redisTemplate;


    @GetMapping({"/", "/index.html"})
    public String indexPage(Model model) {
        //TODO 1,查出所有的1级分类
        List<CategoryEntity> categoryEntities = categoryService.getLevel1Category();
        model.addAttribute("categorys", categoryEntities);
        return "index";
    }

    @ResponseBody
    @GetMapping("/index/catalog.json")
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        Map<String, List<Catelog2Vo>> catalogJson = categoryService.getCatalogJson();
        return catalogJson;
    }

    @ResponseBody
    @GetMapping("/hello")
    public String hello() {
        //1,获取同一把锁,只要名字一样就是同一把锁
        RLock lock = redisson.getLock("my-lock");
        //2,加锁
//        lock.lock();//阻塞式等待.默认加的锁都是30s时间
        //锁的自动续期,如果业务超长,运行期间自动给锁续上新的30s,不用担心业务时间长,锁会自动过期被删除调
        //加锁的业务只要运行完成,就不会给当前续期,即使不手动解锁,锁默认在30s以后自动删除
        lock.lock(10, TimeUnit.SECONDS);
        try {
            System.out.println("加锁成功.执行业务..." + Thread.currentThread().getId());
            Thread.sleep(3000);
        } catch (Exception e) {
        } finally {
            //3,解锁  假设解锁代码没有运行, redisson会不会出现死锁
            System.out.println("释放锁......" + Thread.currentThread().getId());
            lock.unlock();
        }
        return "hello";
    }

    //保证一定能读取到最新数据,修改期间,写锁是一个排它锁(互斥锁),读锁是一个共享锁
    //写锁每释放读就必须等待
    @GetMapping("/write")
    @ResponseBody
    public String writeValue() {
        String s = "";
        try {
            //改数据加写锁,读数据加读锁
            s = UUID.randomUUID().toString();
            Thread.sleep(3000);
            redisTemplate.opsForValue().set("writeValue", s);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return s;
    }


    @GetMapping("/read")
    @ResponseBody
    public String readValue() {
        RReadWriteLock lock = redisson.getReadWriteLock("rw-lock");
        String s = "";
        //加读锁
        RLock rLock = lock.readLock();
        rLock.lock();
        try {
            s = redisTemplate.opsForValue().get("writeValue");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    @GetMapping("/lockDoor")
    @ResponseBody
    public String lockDoor() throws Exception {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.trySetCount(5);
        door.await();//等待闭锁都完成
        return "放假了......";
    }

    @GetMapping("/gogogo/{id}")
    public String gogogo(@PathVariable("id") Long id) {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.countDown();//计数减一
        return id + "班的人都走了......";
    }


}
