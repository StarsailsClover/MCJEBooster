/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.mcjebooster.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.mcjebooster.adapter.VersionAdapter;

/**
 * 反射工具类用于访问 Minecraft 内部对象
 * 
 * 由于 Minecraft 使用不同的混淆映射（MCP, Yarn, Mojang），
 * 我们需要通过反射来动态访问字段和方法。
 * 
 * 此类提供缓存机制以提高性能。
 * 
 * @author StarsailsClover
 * @version 26.5-20260510
 * @since 1.0
 */
public class ReflectionHelper {
    
    /** 字段缓存：类名 -> 字段名 -> Field */
    private static final Map<String, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    
    /** 方法缓存：类名 -> 方法签名 -> Method */
    private static final Map<String, Map<String, Method>> methodCache = new ConcurrentHashMap<>();
    
    /** 类缓存：类名 -> Class */
    private static final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    
    /**
     * 获取对象的字段值（支持私有字段）
     * 
     * @param obj 目标对象
     * @param fieldNames 可能的字段名列表（用于处理不同映射）
     * @return 字段值，如果未找到返回 null
     */
    public static Object getFieldValue(Object obj, String... fieldNames) {
        if (obj == null || fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        
        Class<?> clazz = obj.getClass();
        String cacheKey = clazz.getName();
        
        Map<String, Field> classFields = fieldCache.computeIfAbsent(
            cacheKey, 
            k -> new ConcurrentHashMap<>()
        );
        
        for (String fieldName : fieldNames) {
            try {
                Field field = classFields.get(fieldName);
                
                if (field == null) {
                    // 尝试在当前类中查找
                    field = findField(clazz, fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        classFields.put(fieldName, field);
                    }
                }
                
                if (field != null) {
                    return field.get(obj);
                }
                
            } catch (Exception e) {
                Logger.debug("Failed to get field " + fieldName + " from " + clazz.getName() + ": " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 设置对象的字段值（支持私有字段）
     * 
     * @param obj 目标对象
     * @param value 要设置的值
     * @param fieldNames 可能的字段名列表
     * @return 是否成功设置
     */
    public static boolean setFieldValue(Object obj, Object value, String... fieldNames) {
        if (obj == null || fieldNames == null || fieldNames.length == 0) {
            return false;
        }
        
        Class<?> clazz = obj.getClass();
        String cacheKey = clazz.getName();
        
        Map<String, Field> classFields = fieldCache.computeIfAbsent(
            cacheKey, 
            k -> new ConcurrentHashMap<>()
        );
        
        for (String fieldName : fieldNames) {
            try {
                Field field = classFields.get(fieldName);
                
                if (field == null) {
                    field = findField(clazz, fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        classFields.put(fieldName, field);
                    }
                }
                
                if (field != null) {
                    field.set(obj, value);
                    return true;
                }
                
            } catch (Exception e) {
                Logger.debug("Failed to set field " + fieldName + " in " + clazz.getName() + ": " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * 调用对象的方法（支持私有方法）
     * 
     * @param obj 目标对象
     * @param methodNames 可能的方法名列表
     * @param args 方法参数
     * @return 方法返回值
     */
    public static Object invokeMethod(Object obj, String[] methodNames, Object... args) {
        if (obj == null || methodNames == null || methodNames.length == 0) {
            return null;
        }
        
        Class<?> clazz = obj.getClass();
        String cacheKey = clazz.getName();
        
        Map<String, Method> classMethods = methodCache.computeIfAbsent(
            cacheKey, 
            k -> new ConcurrentHashMap<>()
        );
        
        Class<?>[] argTypes = getArgTypes(args);
        
        for (String methodName : methodNames) {
            try {
                String methodKey = methodName + Arrays.toString(argTypes);
                Method method = classMethods.get(methodKey);
                
                if (method == null) {
                    method = findMethod(clazz, methodName, argTypes);
                    if (method != null) {
                        method.setAccessible(true);
                        classMethods.put(methodKey, method);
                    }
                }
                
                if (method != null) {
                    return method.invoke(obj, args);
                }
                
            } catch (Exception e) {
                Logger.debug("Failed to invoke method " + methodName + " on " + clazz.getName() + ": " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 在类及其父类中查找字段
     * 
     * @param clazz 起始类
     * @param fieldName 字段名
     * @return 找到的字段，未找到返回 null
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // 继续在父类中查找
                current = current.getSuperclass();
            }
        }
        
        return null;
    }
    
    /**
     * 在类及其父类中查找方法
     * 
     * @param clazz 起始类
     * @param methodName 方法名
     * @param argTypes 参数类型
     * @return 找到的方法，未找到返回 null
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        Class<?> current = clazz;
        
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, argTypes);
            } catch (NoSuchMethodException e) {
                // 尝试查找参数兼容的方法
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(methodName) && 
                        isCompatible(method.getParameterTypes(), argTypes)) {
                        return method;
                    }
                }
                
                // 继续在父类中查找
                current = current.getSuperclass();
            }
        }
        
        return null;
    }
    
    /**
     * 检查参数类型是否兼容
     * 
     * @param declared 声明的参数类型
     * @param actual 实际参数类型
     * @return 是否兼容
     */
    private static boolean isCompatible(Class<?>[] declared, Class<?>[] actual) {
        if (declared.length != actual.length) {
            return false;
        }
        
        for (int i = 0; i < declared.length; i++) {
            if (actual[i] == null) {
                if (declared[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            
            if (!declared[i].isAssignableFrom(actual[i]) && 
                !isWrapperCompatible(declared[i], actual[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查基本类型和包装类型的兼容性
     * 
     * @param declared 声明的类型
     * @param actual 实际类型
     * @return 是否兼容
     */
    private static boolean isWrapperCompatible(Class<?> declared, Class<?> actual) {
        if (declared == int.class && actual == Integer.class) return true;
        if (declared == long.class && actual == Long.class) return true;
        if (declared == boolean.class && actual == Boolean.class) return true;
        if (declared == double.class && actual == Double.class) return true;
        if (declared == float.class && actual == Float.class) return true;
        if (declared == byte.class && actual == Byte.class) return true;
        if (declared == short.class && actual == Short.class) return true;
        if (declared == char.class && actual == Character.class) return true;
        
        return false;
    }
    
    /**
     * 获取参数的类型数组
     * 
     * @param args 参数数组
     * @return 类型数组
     */
    private static Class<?>[] getArgTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return new Class<?>[0];
        }
        
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass() : null;
        }
        
        return types;
    }
    
    /**
     * 从适配器获取类实例
     * 
     * @param adapter 版本适配器
     * @param classKey 类键（如 "MinecraftServer", "Level" 等）
     * @return 类实例，未找到返回 null
     */
    public static Class<?> getClassFromAdapter(VersionAdapter adapter, String classKey) {
        if (adapter == null || classKey == null) {
            return null;
        }
        
        String className = adapter.getClassMappings().get(classKey);
        if (className == null) {
            return null;
        }
        
        return classCache.computeIfAbsent(className, k -> {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                Logger.warn("Class not found: " + className);
                return null;
            }
        });
    }
    
    /**
     * 获取对象中的集合字段
     * 
     * @param obj 目标对象
     * @param fieldNames 可能的字段名
     * @return 集合对象，未找到返回空列表
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> getCollectionField(Object obj, String... fieldNames) {
        Object value = getFieldValue(obj, fieldNames);
        
        if (value instanceof Collection) {
            return (Collection<T>) value;
        } else if (value instanceof Map) {
            return (Collection<T>) ((Map<?, ?>) value).values();
        }
        
        return Collections.emptyList();
    }
    
    /**
     * 获取对象中的 Map 字段
     * 
     * @param obj 目标对象
     * @param fieldNames 可能的字段名
     * @return Map 对象，未找到返回空 Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMapField(Object obj, String... fieldNames) {
        Object value = getFieldValue(obj, fieldNames);
        
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        
        return Collections.emptyMap();
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearCache() {
        fieldCache.clear();
        methodCache.clear();
        classCache.clear();
        Logger.info("ReflectionHelper cache cleared");
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计字符串
     */
    public static String getCacheStats() {
        int totalFields = fieldCache.values().stream()
            .mapToInt(Map::size)
            .sum();
        int totalMethods = methodCache.values().stream()
            .mapToInt(Map::size)
            .sum();
        
        return String.format(
            "ReflectionHelper Cache Stats - Classes: %d, Fields: %d, Methods: %d",
            classCache.size(),
            totalFields,
            totalMethods
        );
    }
}
