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

package com.mcjebooster.transformer;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import com.mcjebooster.util.Logger;
import com.mcjebooster.adapter.VersionAdapter;

/**
 * ASM Bytecode Transformer for MinecraftServer
 * 
 * This transformer modifies Minecraft's tick loop to enable multi-core
 * region-based scheduling. It uses the ASM library to perform bytecode
 * manipulation at the JVM level.
 * 
 * Transformation targets:
 * - MinecraftServer.tick() or runTick() - Main tick loop
 * - ChunkProvider.tick() - Chunk tick scheduling
 * - Level.tickEntities() - Entity tick scheduling
 * 
 * The transformer identifies methods by bytecode signatures rather than
 * names to handle different obfuscation mappings.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class MinecraftServerTransformer implements ClassFileTransformer, Opcodes {
    
    /** The detected Minecraft version for version-specific transformations */
    private final String minecraftVersion;
    
    /** The version adapter for version-specific transformations */
    private final VersionAdapter versionAdapter;
    
    /** Flag to track if transformation was applied */
    private volatile boolean transformed = false;
    
    /** Counter for transformed classes */
    private static final java.util.concurrent.atomic.AtomicInteger transformCount = 
        new java.util.concurrent.atomic.AtomicInteger(0);
    
    /** The version adapter for version-specific transformations */
    private final VersionAdapter versionAdapter;
    
    /**
     * Constructs a new transformer for the specified Minecraft version
     * 
     * @param minecraftVersion The detected Minecraft version
     */
    public MinecraftServerTransformer(String minecraftVersion) {
        this(minecraftVersion, null);
    }
    
    /**
     * Constructs a new transformer with version adapter
     * 
     * @param minecraftVersion The detected Minecraft version
     * @param versionAdapter The version-specific adapter
     */
    public MinecraftServerTransformer(String minecraftVersion, VersionAdapter versionAdapter) {
        this.minecraftVersion = minecraftVersion;
        this.versionAdapter = versionAdapter;
        Logger.info("MinecraftServerTransformer initialized for version: " + minecraftVersion);
        if (versionAdapter != null) {
            Logger.info("Using adapter: " + versionAdapter.getAdapterId());
        }
    }
    
    /**
     * Transforms the class file bytes
     * This method is called by the JVM when a class is being defined or redefined
     * 
     * @param loader The defining loader of the class
     * @param className The name of the class in internal form
     * @param classBeingRedefined The class being redefined (if applicable)
     * @param protectionDomain The protection domain of the class
     * @param classfileBuffer The bytes of the class file
     * @return The transformed class file bytes, or null if no transformation
     */
    @Override
    public byte[] transform(ClassLoader loader, String className, 
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, 
                          byte[] classfileBuffer) {
        
        // Defensive checks
        if (classfileBuffer == null || classfileBuffer.length == 0) {
            return null;
        }
        
        // Skip null or empty class names
        if (className == null || className.isEmpty()) {
            return null;
        }
        
        // Skip classes from MCJEBooster itself to avoid recursive transformation
        if (className.startsWith("com/mcjebooster/")) {
            return null;
        }
        
        // Skip Java standard library classes
        if (className.startsWith("java/") || className.startsWith("javax/") || 
            className.startsWith("sun/") || className.startsWith("com/sun/")) {
            return null;
        }
        
        try {
            // Check if this is a Minecraft server class
            if (isMinecraftServerClass(className)) {
                Logger.info("Transforming class: " + className);
                return transformMinecraftServer(classfileBuffer);
            }
            
            // Check if this is a chunk provider class
            if (isChunkProviderClass(className)) {
                Logger.info("Transforming chunk provider class: " + className);
                return transformChunkProvider(classfileBuffer);
            }
            
            // Check if this is a level/world class
            if (isLevelClass(className)) {
                Logger.info("Transforming level class: " + className);
                return transformLevel(classfileBuffer);
            }
        } catch (Exception e) {
            Logger.error("Error transforming class " + className + ": " + e.getMessage());
            // Return original bytes on error
            return null;
        }
        
        return null;
    }
    
    /**
     * Checks if the class is the MinecraftServer class
     * Uses multiple heuristics to handle different mappings
     * 
     * @param className The internal class name
     * @return true if this is likely MinecraftServer
     */
    private boolean isMinecraftServerClass(String className) {
        // First check adapter if available
        if (versionAdapter != null) {
            String serverClass = versionAdapter.getClassMappings().get("MinecraftServer");
            if (serverClass != null) {
                String internalName = serverClass.replace('.', '/');
                if (className.equals(internalName) || className.endsWith("/" + internalName)) {
                    return true;
                }
            }
        }
        
        // Fallback to common class names across different mappings
        String[] serverClassNames = {
            "net/minecraft/server/MinecraftServer",
            "net/minecraft/server/dedicated/DedicatedServer",
            "net/minecraft/class_3176", // Yarn intermediary
            "axw", // Some obfuscated versions
            "MinecraftServer"
        };
        
        for (String name : serverClassNames) {
            if (className.equals(name) || className.endsWith("/" + name)) {
                return true;
            }
        }
        
        // Additional check: look for server-specific bytecode patterns
        // This is done during actual transformation
        return false;
    }
    
    /**
     * Checks if the class is a chunk provider class
     * 
     * @param className The internal class name
     * @return true if this is likely a chunk provider
     */
    private boolean isChunkProviderClass(String className) {
        String[] providerNames = {
            "net/minecraft/world/chunk/ChunkProvider",
            "net/minecraft/class_1937", // Some versions
            "ChunkProvider",
            "ServerChunkCache"
        };
        
        for (String name : providerNames) {
            if (className.contains(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if the class is a level/world class
     * 
     * @param className The internal class name
     * @return true if this is likely a level/world class
     */
    private boolean isLevelClass(String className) {
        String[] levelNames = {
            "net/minecraft/world/World",
            "net/minecraft/world/Level",
            "net/minecraft/class_1937",
            "ServerLevel",
            "WorldServer"
        };
        
        for (String name : levelNames) {
            if (className.contains(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Transforms the MinecraftServer class
     * 
     * @param classBytes The original class bytes
     * @return The transformed class bytes
     */
    private byte[] transformMinecraftServer(byte[] classBytes) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            
            boolean modified = false;
            
            // Find and transform the tick method
            for (MethodNode method : cn.methods) {
                if (isTickMethod(method)) {
                    Logger.info("Found tick method: " + method.name + " " + method.desc);
                    injectMultithreadedTick(method);
                    modified = true;
                }
            }
            
            if (modified) {
                ClassWriter cw = new ClassWriter(
                    ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES
                );
                cn.accept(cw);
                transformed = true;
                transformCount.incrementAndGet();
                Logger.info("Successfully transformed MinecraftServer class");
                return cw.toByteArray();
            }
            
        } catch (Exception e) {
            Logger.error("Failed to transform MinecraftServer: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Transforms a chunk provider class
     * 
     * @param classBytes The original class bytes
     * @return The transformed class bytes
     */
    private byte[] transformChunkProvider(byte[] classBytes) {
        // Placeholder for chunk provider transformation
        // This would modify chunk tick scheduling
        return null;
    }
    
    /**
     * Transforms a level/world class
     * 
     * @param classBytes The original class bytes
     * @return The transformed class bytes
     */
    private byte[] transformLevel(byte[] classBytes) {
        // Placeholder for level transformation
        // This would modify entity tick scheduling
        return null;
    }
    
    /**
     * Determines if a method is the main tick method
     * Uses bytecode signatures to identify the method across different mappings
     * 
     * @param method The method node to check
     * @return true if this is likely the tick method
     */
    private boolean isTickMethod(MethodNode method) {
        // First check adapter if available
        if (versionAdapter != null) {
            String tickMethod = versionAdapter.getTickMethodTarget();
            if (tickMethod != null && tickMethod.contains(".")) {
                String methodName = tickMethod.substring(tickMethod.lastIndexOf('.') + 1);
                String expectedDesc = versionAdapter.getMethodDescriptors().get(tickMethod);
                
                if (method.name.equals(methodName)) {
                    if (expectedDesc == null || method.desc.equals(expectedDesc)) {
                        return true;
                    }
                }
            }
        }
        
        // Fallback to method name patterns
        String[] tickMethodNames = {
            "tick",
            "runTick",
            "tickServer",
            "method_3748", // Some yarn mappings
            "m_5705_", // Some MCP mappings
            "a" // Highly obfuscated
        };
        
        for (String name : tickMethodNames) {
            if (method.name.equals(name)) {
                // Additional check: look for tick-specific bytecode patterns
                return hasTickPattern(method);
            }
        }
        
        // If name doesn't match, check bytecode patterns
        return hasTickPattern(method);
    }
    
    /**
     * Checks if a method has the bytecode pattern of a tick method
     * 
     * @param method The method to check
     * @return true if the method has tick patterns
     */
    private boolean hasTickPattern(MethodNode method) {
        // Look for patterns like:
        // - System.nanoTime() calls (for timing)
        // - Thread.sleep() calls (for tick rate limiting)
        // - Loop structures with tick-related operations
        
        boolean hasNanoTime = false;
        boolean hasTickOperations = false;
        
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                
                // Check for System.nanoTime()
                if (minsn.owner.equals("java/lang/System") && 
                    minsn.name.equals("nanoTime")) {
                    hasNanoTime = true;
                }
                
                // Check for tick-related method calls
                if (minsn.name.contains("tick") || 
                    minsn.name.contains("update") ||
                    minsn.name.contains("process")) {
                    hasTickOperations = true;
                }
            }
        }
        
        return hasNanoTime && hasTickOperations;
    }
    
    /**
     * Injects multi-threaded region scheduling into the tick method
     * 
     * @param method The method node to transform
     */
    private void injectMultithreadedTick(MethodNode method) {
        InsnList instructions = method.instructions;
        
        // Find the entry point of the tick loop
        AbstractInsnNode entryPoint = findTickLoopEntry(instructions);
        
        if (entryPoint == null) {
            Logger.warn("Could not find tick loop entry point, injecting at method start");
            entryPoint = instructions.getFirst();
        }
        
        // Create the injection code
        InsnList injectCode = new InsnList();
        
        // Call: RegionScheduler.getInstance().tickRegions(this)
        // LOAD the scheduler instance
        injectCode.add(new MethodInsnNode(
            INVOKESTATIC,
            "com/mcjebooster/scheduler/RegionScheduler",
            "getInstance",
            "()Lcom/mcjebooster/scheduler/RegionScheduler;",
            false
        ));
        
        // LOAD 'this' (MinecraftServer instance)
        injectCode.add(new VarInsnNode(ALOAD, 0));
        
        // CALL tickRegions
        injectCode.add(new MethodInsnNode(
            INVOKEVIRTUAL,
            "com/mcjebooster/scheduler/RegionScheduler",
            "tickRegions",
            "(Ljava/lang/Object;)V",
            false
        ));
        
        // Insert the injection code
        instructions.insertBefore(entryPoint, injectCode);
        
        Logger.info("Injected multi-threaded tick scheduling into method: " + method.name);
    }
    
    /**
     * Finds the entry point of the tick loop within a method
     * 
     * @param instructions The instruction list
     * @return The entry point instruction, or null if not found
     */
    private AbstractInsnNode findTickLoopEntry(InsnList instructions) {
        // Look for patterns that indicate the start of the tick loop
        // This could be a label, a loop header, or specific bytecode patterns
        
        AbstractInsnNode current = instructions.getFirst();
        
        while (current != null) {
            // Look for common tick loop patterns
            if (current instanceof LabelNode) {
                // Check if this label is followed by tick-related operations
                AbstractInsnNode next = current.getNext();
                if (next != null && isTickOperation(next)) {
                    return current;
                }
            }
            
            current = current.getNext();
        }
        
        return null;
    }
    
    /**
     * Checks if an instruction is a tick-related operation
     * 
     * @param insn The instruction to check
     * @return true if the instruction is tick-related
     */
    private boolean isTickOperation(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode minsn = (MethodInsnNode) insn;
            String name = minsn.name.toLowerCase();
            return name.contains("tick") || 
                   name.contains("update") || 
                   name.contains("process");
        }
        return false;
    }
    
    /**
     * Returns whether transformation was applied
     * 
     * @return true if the class was transformed
     */
    public boolean isTransformed() {
        return transformed;
    }
    
    /**
     * Gets the total number of classes transformed
     * 
     * @return The transform count
     */
    public static int getTransformCount() {
        return transformCount.get();
    }
}
