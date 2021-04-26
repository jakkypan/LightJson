package com.panda.lightjson;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

/**
 * @author panda
 * created at 2021/3/17 11:06 AM
 */
@RunWith(AndroidJUnit4.class)
public class JsonTest {
    @Test
    public void test1() {
        Bean1 bean1 = LightJson.fromJson("{\"a\":\"a1\", \"b\":10}", Bean1.class);
        assertEquals(bean1.a, "a1");
        assertEquals(bean1.getB(), 10);
    }

    @Test
    public void test2() {
        Bean2 bean2 = LightJson.fromJson("{\"a\":\"a1\", \"b\":[1,2,3]}", Bean2.class);
        assertEquals(bean2.a, "a1");
        assertEquals((long) bean2.getB().get(0), 1);
        assertEquals((long) bean2.getB().get(1), 2);
        assertEquals((long) bean2.getB().get(2), 3);
    }

    @Test
    public void test3() {
        Bean3 bean3 = LightJson.fromJson("{\"a\":\"a1\", \"b\":[{\"a\":\"a1\", \"b\":10},{\"a\":\"a2\", \"b\":11}]}", Bean3.class);
        assertEquals(bean3.a, "a1");
        Bean1 bean1 = bean3.getB().get(0);
        assertEquals(bean1.a, "a1");
        assertEquals(bean1.getB(), 10);
        bean1 = bean3.getB().get(1);
        assertEquals(bean1.a, "a2");
        assertEquals(bean1.getB(), 11);
    }

    @Test
    public void test4() {
        Bean4 bean4 = LightJson.fromJson("{\"a\":[[1,2],[3,4]]}", Bean4.class);
        ArrayList<Integer> list = bean4.a.get(0);
        assertEquals((long)list.get(0), 1);
        assertEquals((long)list.get(1), 2);
        list = bean4.a.get(1);
        assertEquals((long)list.get(0), 3);
        assertEquals((long)list.get(1), 4);
    }

    @Test
    public void test5() {
        Bean5 bean5 = LightJson.fromJson("{\"a\":1,\"b\":\"b\"}", Bean5.class);
        assertEquals(bean5.a, 1);
        assertEquals(bean5.b, "b");
    }

    @Test
    public void test6() {
        Bean6 bean6 = LightJson.fromJson("{\"a\":1,\"ib\":{\"b\":2}}", Bean6.class);
        assertEquals(bean6.a, 1);
        assertEquals(bean6.ib.b, 2);
    }
}
