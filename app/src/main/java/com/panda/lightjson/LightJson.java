package com.panda.lightjson;

import android.text.TextUtils;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * 功能：
 * 1、支持java的所有基本类型
 * 2、支持java bean，并且支持java bean的嵌套
 * 3、支持list结构，list泛型支持基本类型和java bean
 * 4、支持类型过滤
 * 5、支持注解修改类型名称
 * 6、支持缓存
 * 7、支持任何层数的父类继承
 * 8、支持list任何层数嵌套list，如ArrayList<ArrayList<ArrayList<Integer>>>
 * 9、支持泛型
 *
 * 不支持：
 * 1、bean的泛型
 * 2、非静态内部类 https://www.jianshu.com/p/b10d006a14fd
 *
 * TIPS：
 * 如果java bean有了非空的构造函数，则必须提供空的构造函数
 *
 * Usage：
 *
 * public class Bean {
 *     @RealName(value = "as")
 *     private String a;
 *     private int b;
 *     private boolean c;
 * }
 * Bean bean = LightJson.fromJson("{\"as\":\"a1\",\"b\":10,\"c\":false}", Bean::class.java)
 *
 * @author panda
 * created at 2021/3/12 7:35 PM
 */
public final class LightJson {
    public static <T> T fromJson(String json, Class<T> type) {
        if (TextUtils.isEmpty(json)) return null;
        T cache = LruMemoryCache.get(LruMemoryCache.md5(json));
        if (cache != null) return cache;

        JSONObject jsonObject = null;
        T t = null;
        try {
            jsonObject = new JSONObject(json);
            t = type.newInstance();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (null != t) {
            parseEntity(jsonObject, t);
            LruMemoryCache.put(LruMemoryCache.md5(json), t);
            return t;
        }
        return null;
    }

    //-----------------private------------------//

    private static <T> void parseEntity(JSONObject jsonObject, T t) {
        // 获取当前类的所有field，不包括父类
        Field[] fields = t.getClass().getDeclaredFields();
        ArrayList<Field> listFields = new ArrayList<>(Arrays.asList(fields));
        // 找到所有父类里的所有field
        Class<?> superClass = t.getClass().getSuperclass();
        while (superClass != null) {
            Field[] fs = superClass.getDeclaredFields();
            if (fs.length > 0) {
                listFields.addAll(Arrays.asList(fs));
            }
            superClass = superClass.getSuperclass();
        }
        for (Field field : listFields) {
            if (!field.isAnnotationPresent(Ignore.class)) {
                try {
                    parseEveryField(field, jsonObject, t);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static <T> void parseEveryField(Field field, JSONObject jsonObject, T t) throws Exception {
        field.setAccessible(true);
        // 获取到key值
        String fieldName = field.getName();
        if (field.isAnnotationPresent(RealName.class)) {
            fieldName = Objects.requireNonNull(field.getAnnotation(RealName.class)).value();
        }

        Type fieldType = field.getType();
        FieldType ft = getFiledType(fieldType);
        if (ft == FieldType.BEAN) { // 处理自定义java bean
            setJavaBeanValue(field, jsonObject, t, fieldType, fieldName);
        } else if (ft == FieldType.LIST) {  // 处理集合类
            setListValue(field, jsonObject, fieldName, t);
        } else {  // 处理基本类型
            setBasicValue(field, jsonObject, t, fieldName, ft);
        }
        field.setAccessible(false);
    }

    private static <T> void setListValue(Field field, JSONObject jsonObject, String fieldName, T t) throws Exception {
        // 实例化出列表的对象，实例化对象只能通过getType，因为java的泛型会擦除类型
        // https://stackoverflow.com/questions/4818228/how-to-instantiate-a-java-util-arraylist-with-generic-class-using-reflection
        Type fieldType = field.getType();
        Collection collection = (Collection) generateInstance(fieldType);
        // https://zhixiangyuan.github.io/2019/12/17/field-%E7%9A%84-gettype-%E4%B8%8E-getgenerictype-%E7%9A%84%E5%8C%BA%E5%88%AB/
        // getGenericType 返回的是带泛型的类型
        // 通过genericType来得到Collection里的泛型类
        Type fieldGenericType = field.getGenericType();
        ParameterizedType parameterizedType = (ParameterizedType) fieldGenericType;
        Type genericType = parameterizedType.getActualTypeArguments()[0];
        FieldType listFt = getFiledType(genericType);
        // 将array内容解析出来
        JSONArray jsonArray = jsonObject.getJSONArray(fieldName);
        if (listFt == FieldType.BEAN) {
            setCollectionJavaBeanValue(collection, jsonArray, genericType);
        } else if (listFt == FieldType.LIST) {
            setCollectionListValueList(collection, jsonArray, genericType);
        } else {
            setCollectionBasicValue(collection, jsonArray, genericType);
        }
        field.set(t, collection);
    }

    private static void setCollectionBasicValue(
            Collection collection, JSONArray jsonArray, Type genericType) throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) {
            collection.add(((Class) genericType).cast(jsonArray.get(i)));
        }
    }

    private static void setCollectionJavaBeanValue(
            Collection collection, JSONArray jsonArray, Type genericType) throws Exception {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject value = (JSONObject) jsonArray.get(i);
            Object bean = generateInstance(genericType);
            if (bean != null) {
                parseEntity(value, bean);
                collection.add(bean);
            }
        }
    }

    private static void setCollectionListValueList(
            Collection collection, JSONArray jsonArray, Type genericType) throws Exception {
        for (int i = 0; i < jsonArray.length(); i++) {
            setCollectionListValue(collection, jsonArray.getJSONArray(i), genericType);
        }
    }

    private static void setCollectionListValue(
            Collection collection, JSONArray jsonArray, Type genericType) throws Exception {
        Type type = ((ParameterizedType) genericType).getRawType();
        Collection c = (Collection) generateInstance(type);
        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type realGenericType = parameterizedType.getActualTypeArguments()[0];
        FieldType listFt = getFiledType(realGenericType);
        if (listFt == FieldType.BEAN) {
            setCollectionJavaBeanValue(c, jsonArray, realGenericType);
        } else if (listFt == FieldType.LIST) {
            setCollectionListValueList(c, jsonArray, realGenericType);
        } else { // 基本类型
            setCollectionBasicValue(c, jsonArray, realGenericType);
        }
        collection.add(c);
    }

    private static <T> void setBasicValue(Field field, JSONObject jsonObject,
                                   T t, String fieldName, FieldType fieldType) throws Exception {
        Object value = null;
        switch (fieldType) {
            case INT:
                value = jsonObject.getInt(fieldName);
                break;
            case LONG:
                value = jsonObject.getLong(fieldName);
                break;
            case DOUBLE:
                value = jsonObject.getDouble(fieldName);
                break;
            case FLOAT:
                value = (float) jsonObject.getDouble(fieldName);
                break;
            case BOOLEAN:
                value = jsonObject.getBoolean(fieldName);
                break;
            case STRING:
                value = jsonObject.getString(fieldName);
                break;
            default:
                value = jsonObject.get(fieldName);
        }
        field.set(t, value);

    }

    private static FieldType getFiledType(Type type) {
        if (type == int.class || type == Integer.class) {
            return FieldType.INT;
        }
        if (type == long.class || type == Long.class) {
            return FieldType.LONG;
        }
        if (type == boolean.class || type == Boolean.class) {
            return FieldType.BOOLEAN;
        }
        if (type == double.class || type == Double.class) {
            return FieldType.DOUBLE;
        }
        if (type == float.class || type == Float.class) {
            return FieldType.FLOAT;
        }
        if (type == String.class) {
            return FieldType.STRING;
        }
        if (type instanceof ParameterizedType) {
            if (Collection.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType())) {
                return FieldType.LIST;
            }
        }
        if (Collection.class.isAssignableFrom((Class<?>) type)) {
            return FieldType.LIST;
        }
        return FieldType.BEAN;
    }

    /**
     * 因为getType获取类型会带上前缀
     */
    private final static String CLASS_PREFIX = "class ";
    private final static String INTERFACE_PREFIX = "interface ";

    private static String getClassName(Type type) {
        String fullName = type.toString();
        if (fullName.startsWith(CLASS_PREFIX))
            return fullName.substring(CLASS_PREFIX.length());
        if (fullName.startsWith(INTERFACE_PREFIX))
            return fullName.substring(INTERFACE_PREFIX.length());
        return fullName;
    }

    private static Object generateInstance(Type type) {
        try {
            Class<?> genericsType = Class.forName(getClassName(type));
            return genericsType.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static <T> void setJavaBeanValue(Field field, JSONObject jsonObject, T t,
                                    Type fieldType, String fieldName) throws Exception {
        JSONObject value = jsonObject.getJSONObject(fieldName);
        Object o = generateInstance(fieldType);
        // 递归执行新的对象，重新开始
        if (o != null) {
            parseEntity(value, o);
        }
        field.set(t, o);
    }

    private enum FieldType {
        INT,
        LONG,
        BOOLEAN,
        FLOAT,
        DOUBLE,
        STRING,
        LIST,  // 集合类型
        BEAN   // 自定义java bean
    }

    private static class LruMemoryCache {
        private final static LruCache<String, Object> lruCache;
        static {
            lruCache = new LruCache<String, Object>(1024 * 1024 / 2) { //512KB的缓存
                @Override
                protected int sizeOf(String key, Object value) {
                    try {
                        return LruMemoryCache.sizeOf(value);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    return super.sizeOf(key, value) * 10;
                }
            };
        }

        public static void put(String key, Object value) {
            lruCache.put(key, value);
        }

        public static <T> T get(String key) {
            Object o = lruCache.get(key);
            if (o != null) {
                return (T) o;
            }
            return null;
        }

        public static void clear() {
            lruCache.evictAll();
        }

        public static String md5(String origin) {
            byte[] digest = null;
            try {
                MessageDigest md5 = MessageDigest.getInstance("md5");
                digest  = md5.digest(origin.getBytes("utf-8"));
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return new BigInteger(1, digest).toString(16);
        }
        /**
         * 返回java bean的大小，字节为单位
         *
         * @param object
         * @return
         */
        private static int sizeOf(Object object) throws IllegalAccessException {
            int result = 0;
            if (object == null) return result;
            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (!Modifier.isStatic(field.getModifiers())) {
                    FieldType fieldType = getFiledType(field.getType());
                    switch (fieldType) {
                        case INT:
                            result += Integer.SIZE / Byte.SIZE;
                            break;
                        case LONG:
                            result += Long.SIZE / Byte.SIZE;
                            break;
                        case DOUBLE:
                            result += Double.SIZE / Byte.SIZE;
                            break;
                        case FLOAT:
                            result += Float.SIZE / Byte.SIZE;
                            break;
                        case BOOLEAN:
                            result += 1;
                            break;
                        case STRING:
                            // 严格意义应该怎么计算分ASCII和Unicode，可参考：https://www.quora.com/How-many-bytes-are-needed-to-store-string
                            // 这里先简单处理了
                            Object o = field.get(object);
                            if (o != null) {
                                String value = (String) o;
                                result += value.length();
                            }
                            break;
                        case LIST:
                            // https://plumbr.io/blog/memory-leaks/calculating-the-size-of-java-collections
                            Object o1 = field.get(object);
                            if (o1 == null) break;
                            Collection c = (Collection) o1;
                            int csize = c.size();
                            Type fieldGenericType = field.getGenericType();
                            ParameterizedType parameterizedType = (ParameterizedType) fieldGenericType;
                            Type genericType = parameterizedType.getActualTypeArguments()[0];
                            FieldType ft = getFiledType(genericType);
                            switch (ft) {
                                case INT:
                                    result += csize * (Integer.SIZE / Byte.SIZE);
                                    break;
                                case LONG:
                                    result += csize * (Long.SIZE / Byte.SIZE);
                                    break;
                                case DOUBLE:
                                    result += csize * (Double.SIZE / Byte.SIZE);
                                    break;
                                case FLOAT:
                                    result += csize * (Float.SIZE / Byte.SIZE);
                                    break;
                                case BOOLEAN:
                                    result += csize;
                                    break;
                                case STRING:
                                    Object oo = field.get(object);
                                    if (oo != null) {
                                        String value = (String) oo;
                                        result += csize * value.length();
                                    }
                                    break;
                                case BEAN:
                                    Object bean = field.get(object);
                                    if (bean != null) {
                                        result += sizeOf(bean);
                                    }
                                    break;
                            }
                            break;
                        case BEAN:
                            Object bean = field.get(object);
                            if (bean != null) {
                                result += sizeOf(bean);
                            }
                            break;
                        default:
                            break;
                    }
                }
                field.setAccessible(false);
            }
            return result;
        }
    }
}
