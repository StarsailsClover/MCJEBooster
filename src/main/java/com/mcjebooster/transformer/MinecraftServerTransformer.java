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
        
        // Skip Java standard library, internal, and bytecode manipulation classes
        if (className.startsWith("java/") || className.startsWith("javax/") || 
            className.startsWith("sun/") || className.startsWith("com/sun/") ||
            className.startsWith("jdk/") || className.startsWith("org/")) {
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
            "net/minecraft/server/level/ChunkMap",
            "net/minecraft/world/level/chunk/ChunkSource",
            "net/minecraft/server/level/ServerChunkCache",
            "net/minecraft/world/chunk/ChunkProvider",
            "net/minecraft/class_1937",
        };
        
        for (String name : providerNames) {
            if (className.equals(name) || className.endsWith("/" + name)) {
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
            "net/minecraft/server/level/ServerLevel",
            "net/minecraft/world/level/Level",
            "net/minecraft/world/World",
            "net/minecraft/class_1937",
        };
        
        for (String name : levelNames) {
            if (className.equals(name) || className.endsWith("/" + name)) {
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
            MethodNode tickMethod = findBestTickMethod(cn);
            
            if (tickMethod != null) {
                Logger.info("Found tick method: " + tickMethod.name + " " + tickMethod.desc);
                injectMultithreadedTick(tickMethod);
                modified = true;
            }
            
            if (modified) {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            
            boolean modified = false;
            
            // Find and transform chunk tick methods
            for (MethodNode method : cn.methods) {
                if (isChunkTickMethod(method)) {
                    Logger.info("Found chunk tick method: " + method.name + " " + method.desc);
                    // 区块 tick 已经通过 RegionScheduler 处理，这里标记但不修改
                    modified = true;
                }
            }
            
            if (modified) {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                transformCount.incrementAndGet();
                Logger.info("Successfully transformed ChunkProvider class");
                return cw.toByteArray();
            }
            
        } catch (Exception e) {
            Logger.error("Failed to transform ChunkProvider: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Transforms a level/world class
     * 
     * @param classBytes The original class bytes
     * @return The transformed class bytes
     */
    private byte[] transformLevel(byte[] classBytes) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            
            boolean modified = false;
            
            // Find and transform entity tick methods
            for (MethodNode method : cn.methods) {
                if (isEntityTickMethod(method)) {
                    Logger.info("Found entity tick method: " + method.name + " " + method.desc);
                    // 实体 tick 已经通过 RegionScheduler 处理，这里标记但不修改
                    modified = true;
                }
            }
            
            if (modified) {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cn.accept(cw);
                transformCount.incrementAndGet();
                Logger.info("Successfully transformed Level class");
                return cw.toByteArray();
            }
            
        } catch (Exception e) {
            Logger.error("Failed to transform Level: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Checks if a method is a chunk tick method
     * 
     * @param method The method to check
     * @return true if this is a chunk tick method
     */
    private boolean isChunkTickMethod(MethodNode method) {
        String[] chunkTickNames = {
            "tick", "tickChunk", "tickChunks", "func_73156_b"
        };
        
        for (String name : chunkTickNames) {
            if (method.name.equals(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a method is an entity tick method
     * 
     * @param method The method to check
     * @return true if this is an entity tick method
     */
    private boolean isEntityTickMethod(MethodNode method) {
        String[] entityTickNames = {
            "tickEntities", "tickNonPassenger", "guardEntityTick", "func_217390_a"
        };
        
        for (String name : entityTickNames) {
            if (method.name.equals(name)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Finds the best matching main tick method in MinecraftServer.
     * Modern vanilla builds can use fully obfuscated names such as a(BooleanSupplier),
     * so this uses descriptors and bytecode features instead of relying on names.
     *
     * @param classNode The MinecraftServer class node
     * @return the best tick method, or null if no safe candidate is found
     */
    private MethodNode findBestTickMethod(ClassNode classNode) {
        MethodNode best = null;
        int bestScore = 0;
        
        for (MethodNode method : classNode.methods) {
            int score = scoreTickMethod(method);
            if (score > 0) {
                Logger.info("Tick candidate: " + method.name + " " + method.desc + " score=" + score);
            }
            if (score > bestScore) {
                bestScore = score;
                best = method;
            }
        }
        
        if (best != null && bestScore >= 80) {
            Logger.info("Selected tick candidate: " + best.name + " " + best.desc + " score=" + bestScore);
            return best;
        }
        
        Logger.warn("No safe MinecraftServer tick method candidate found; bestScore=" + bestScore);
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
     * Scores how likely a method is to be the main server tick entry.
     *
     * @param method The method to score
     * @return likelihood score
     */
    private int scoreTickMethod(MethodNode method) {
        if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_STATIC)) != 0) {
            return 0;
        }
        
        int score = 0;
        
        if ("(Ljava/util/function/BooleanSupplier;)V".equals(method.desc)) {
            score += 50;
        } else if ("()V".equals(method.desc)) {
            score += 20;
        } else {
            return 0;
        }
        
        if (method.name.equals("tick") || method.name.equals("runTick") ||
            method.name.equals("tickServer") || method.name.equals("method_3748") ||
            method.name.equals("m_5705_") || method.name.equals("a")) {
            score += 20;
        }
        
        boolean callsBooleanSupplierTick = false;
        boolean hasProfilerTickSection = false;
        boolean hasLevelIteration = false;
        boolean hasTiming = false;
        boolean hasPlayerOrConnectionTick = false;
        int methodCalls = 0;
        int fieldWrites = 0;
        
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode minsn = (MethodInsnNode) insn;
                methodCalls++;
                
                if (minsn.desc != null && minsn.desc.contains("Ljava/util/function/BooleanSupplier;") &&
                    minsn.name.length() <= 4) {
                    callsBooleanSupplierTick = true;
                }
                if ((minsn.name.equals("nanoTime") && minsn.owner.equals("java/lang/System")) ||
                    (minsn.name.equals("d") && minsn.desc.equals("()J"))) {
                    hasTiming = true;
                }
                if (minsn.owner.equals("java/lang/Iterable") || minsn.owner.equals("java/util/Iterator") ||
                    minsn.owner.equals("java/util/Collection") || minsn.owner.equals("java/util/List")) {
                    hasLevelIteration = true;
                }
                if (minsn.name.equals("tick") || minsn.name.equals("update") || minsn.name.equals("process") ||
                    minsn.name.equals("forEach") || minsn.name.equals("d")) {
                    hasPlayerOrConnectionTick = true;
                }
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode finsn = (FieldInsnNode) insn;
                if (finsn.getOpcode() == PUTFIELD) {
                    fieldWrites++;
                }
            } else if (insn instanceof LdcInsnNode) {
                Object cst = ((LdcInsnNode) insn).cst;
                if ("tick".equals(cst) || "levels".equals(cst) || "connection".equals(cst) ||
                    "players".equals(cst) || "tallying".equals(cst)) {
                    hasProfilerTickSection = true;
                }
            }
        }
        
        if (callsBooleanSupplierTick) score += 35;
        if (hasProfilerTickSection) score += 30;
        if (hasLevelIteration) score += 20;
        if (hasTiming) score += 15;
        if (hasPlayerOrConnectionTick) score += 10;
        if (methodCalls >= 8) score += 10;
        if (fieldWrites >= 2) score += 10;
        if (method.instructions.size() > 120) score += 10;
        
        return score;
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
        
        // Call: InjectionBridge.tickRegions(this)
        injectCode.add(new VarInsnNode(ALOAD, 0));
        injectCode.add(new MethodInsnNode(
            INVOKESTATIC,
            "com/mcjebooster/agent/InjectionBridge",
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
