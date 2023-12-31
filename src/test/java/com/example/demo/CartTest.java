package com.example.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class CartTest {

    /*
     * IMPORTANT: This test class requires that a Redis server be running on
     *            localhost and listening on port 6379 (the default port).
     */

    @Autowired
    private RedisConnectionFactory cf;

    @Autowired
    private RedisTemplate<String, Product> redisTemplate;

    @BeforeEach
    public void setup() {
        redisTemplate.delete("9781617291203");
        redisTemplate.delete("cart");
        redisTemplate.delete("cart1");
        redisTemplate.delete("cart2");
    }

    @Test
    public void workingWithSimpleValues() {
        Product product = new Product();
        product.setSku("9781617291203");
        product.setName("Spring in Action");
        product.setPrice(39.99f);

        redisTemplate.opsForValue().set(product.getSku(), product);

        Product found = redisTemplate.opsForValue().get(product.getSku());
        assertEquals(product.getSku(), found.getSku());
        assertEquals(product.getName(), found.getName());
        assertEquals(product.getPrice(), found.getPrice(), 0.005);
    }

    @Test
    public void workingWithLists() {
        Product product1 = new Product();
        product1.setSku("9781617291203");
        product1.setName("Spring in Action");
        product1.setPrice(39.99f);

        Product product2 = new Product();
        product2.setSku("9781935182436");
        product2.setName("Spring Integration in Action");
        product2.setPrice(49.99f);

        Product product3 = new Product();
        product3.setSku("9781935182955");
        product3.setName("Spring Batch in Action");
        product3.setPrice(49.99f);

        redisTemplate.opsForList().rightPush("cart", product1);
        redisTemplate.opsForList().rightPush("cart", product2);
        redisTemplate.opsForList().rightPush("cart", product3);

        assertEquals(3, redisTemplate.opsForList().size("cart").longValue());

        Product first = redisTemplate.opsForList().leftPop("cart");
        Product last = redisTemplate.opsForList().rightPop("cart");

        assertEquals(product1.getSku(), first.getSku());
        assertEquals(product1.getName(), first.getName());
        assertEquals(product1.getPrice(), first.getPrice(), 0.005);

        assertEquals(product3.getSku(), last.getSku());
        assertEquals(product3.getName(), last.getName());
        assertEquals(product3.getPrice(), last.getPrice(), 0.005);

        assertEquals(1, redisTemplate.opsForList().size("cart").longValue());
    }

    @Test
    public void workingWithLists_range() {
        for (int i = 0; i < 30; i++) {
            Product product = new Product();
            product.setSku("SKU-" + i);
            product.setName("PRODUCT " + i);
            product.setPrice(i + 0.99f);
            redisTemplate.opsForList().rightPush("cart", product);
        }

        assertEquals(30, redisTemplate.opsForList().size("cart").longValue());

        List<Product> products = redisTemplate.opsForList().range("cart", 2, 12);
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            assertEquals("SKU-" + (i + 2), product.getSku());
            assertEquals("PRODUCT " + (i + 2), product.getName());
            assertEquals(i + 2 + 0.99f, product.getPrice(), 0.005);
        }
    }

    @Test
    public void performingOperationsOnSets() {
        Product product = new Product();
        product.setSku("9781617291203");
        product.setName("Spring in Action");
        product.setPrice(39.99f);

        redisTemplate.opsForSet().add("cart", product);
        assertEquals(1, redisTemplate.opsForSet().size("cart").longValue());
    }

    @Test
    public void performingOperationsOnSets_setOperations() {
        for (int i = 0; i < 30; i++) {
            Product product = new Product();
            product.setSku("SKU-" + i);
            product.setName("PRODUCT " + i);
            product.setPrice(i + 0.99f);
            redisTemplate.opsForSet().add("cart1", product);
            if (i % 3 == 0) {
                redisTemplate.opsForSet().add("cart2", product);
            }
        }

        Set<Product> diff = redisTemplate.opsForSet().difference("cart1", "cart2");
        Set<Product> union = redisTemplate.opsForSet().union("cart1", "cart2");
        Set<Product> isect = redisTemplate.opsForSet().intersect("cart1", "cart2");

        assertEquals(20, diff.size());
        assertEquals(30, union.size());
        assertEquals(10, isect.size());

        Product random = redisTemplate.opsForSet().randomMember("cart1");
        // not sure what to assert here...the result will be random
        assertNotNull(random);
    }

    @Test
    public void bindingToAKey() {
        Product product1 = new Product();
        product1.setSku("9781617291203");
        product1.setName("Spring in Action");
        product1.setPrice(39.99f);

        Product product2 = new Product();
        product2.setSku("9781935182436");
        product2.setName("Spring Integration in Action");
        product2.setPrice(49.99f);

        Product product3 = new Product();
        product3.setSku("9781935182955");
        product3.setName("Spring Batch in Action");
        product3.setPrice(49.99f);

        BoundListOperations<String, Product> cart = redisTemplate.boundListOps("cart");
        cart.rightPush(product1);
        cart.rightPush(product2);
        cart.rightPush(product3);

        assertEquals(3, cart.size().longValue());

        Product first = cart.leftPop();
        Product last = cart.rightPop();

        assertEquals(product1.getSku(), first.getSku());
        assertEquals(product1.getName(), first.getName());
        assertEquals(product1.getPrice(), first.getPrice(), 0.005);

        assertEquals(product3.getSku(), last.getSku());
        assertEquals(product3.getName(), last.getName());
        assertEquals(product3.getPrice(), last.getPrice(), 0.005);

        assertEquals(1, cart.size().longValue());
    }

    @Test
    public void settingKeyAndValueSerializers() {
        // need a local version so we can tweak the serializer
        RedisTemplate<String, Product> redis = new RedisTemplate<>();
        redis.setConnectionFactory(cf);

        redis.setKeySerializer(new StringRedisSerializer());
        redis.setValueSerializer(new Jackson2JsonRedisSerializer<Product>(Product.class));
        redis.afterPropertiesSet(); // if this were declared as a bean, you wouldn't have to do this

        Product product = new Product();
        product.setSku("9781617291203");
        product.setName("Spring in Action");
        product.setPrice(39.99f);

        redis.opsForValue().set(product.getSku(), product);

        Product found = redis.opsForValue().get(product.getSku());
        assertEquals(product.getSku(), found.getSku());
        assertEquals(product.getName(), found.getName());
        assertEquals(product.getPrice(), found.getPrice(), 0.005);

        StringRedisTemplate stringRedis = new StringRedisTemplate(cf);
        String json = stringRedis.opsForValue().get(product.getSku());
        assertEquals("{\"sku\":\"9781617291203\",\"name\":\"Spring in Action\",\"price\":39.99}", json);
    }

}
