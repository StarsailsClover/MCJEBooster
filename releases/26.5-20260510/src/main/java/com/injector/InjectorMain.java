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

package com.mcjebooster.injector;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.util.List;
import java.util.Scanner;

import com.mcjebooster.util.Logger;

/**
 * External Injector for MCJEBooster
 * 
 * This is a standalone application that can attach the MCJEBooster agent
 * to a running Minecraft process. It uses the Java Attach API to find
 * and connect to Minecraft JVM processes.
 * 
 * Usage:
 *   java -jar MCJEBooster-26.1-05102026.jar
 *   
 * Or programmatically:
 *   InjectorMain.main(new String[]{"--auto"});
 * 
 * IMPORTANT: This injector requires the JDK's tools.jar (or equivalent)
 * to be available on the classpath.
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class InjectorMain {
    
    /** Path to the agent JAR file */
    private static String agentPath;
    
    /** Auto-inject mode flag */
    private static boolean autoMode = false;
    
    /** Force injection flag (skip confirmation) */
    private static boolean forceMode = false;
    
    /** 
     * Main entry point for the injector application
     * 
     * @param args Command line arguments:
     *             --auto    : Automatically inject into the first Minecraft process found
     *             --force   : Skip confirmation prompts
     *             --help    : Display help message
     */
    public static void main(String[] args) {
        Logger.info("=========================================");
        Logger.info("MCJEBooster External Injector");
        Logger.info("Version: 26.1-05102026");
        Logger.info("=========================================");
        
        // Parse command line arguments
        parseArguments(args);
        
        // Display help if requested
        if (hasArgument(args, "--help") || hasArgument(args, "-h")) {
            printHelp();
            return;
        }
        
        // Get the path to the agent JAR
        agentPath = getAgentPath();
        if (agentPath == null) {
            Logger.error("Failed to locate MCJEBooster agent JAR");
            Logger.error("Ensure the injector is running from the same directory as the agent JAR");
            System.exit(1);
        }
        
        Logger.info("Agent JAR located at: " + agentPath);
        
        // Find Minecraft processes
        List<MinecraftProcess> mcProcesses = findMinecraftProcesses();
        
        if (mcProcesses.isEmpty()) {
            Logger.warn("No Minecraft processes found!");
            Logger.info("Please start Minecraft first, then run this injector.");
            System.exit(1);
        }
        
        Logger.info("Found " + mcProcesses.size() + " Minecraft process(es)");
        
        // Select target process
        MinecraftProcess target = selectTargetProcess(mcProcesses);
        
        if (target == null) {
            Logger.info("No process selected. Exiting.");
            System.exit(0);
        }
        
        // Confirm injection
        if (!forceMode && !confirmInjection(target)) {
            Logger.info("Injection cancelled by user.");
            System.exit(0);
        }
        
        // Perform injection
        boolean success = injectAgent(target);
        
        if (success) {
            Logger.info("=========================================");
            Logger.info("Injection completed successfully!");
            Logger.info("MCJEBooster is now active in the target process.");
            Logger.info("=========================================");
            System.exit(0);
        } else {
            Logger.error("=========================================");
            Logger.error("Injection failed!");
            Logger.error("Check the logs above for details.");
            Logger.error("=========================================");
            System.exit(1);
        }
    }
    
    /**
     * Parses command line arguments
     * 
     * @param args The command line arguments
     */
    private static void parseArguments(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg == null) continue;
            switch (arg) {
                case "--auto":
                    autoMode = true;
                    break;
                case "--force":
                case "-f":
                    forceMode = true;
                    break;
            }
        }
    }
    
    /**
     * Checks if a specific argument is present
     * 
     * @param args The arguments array
     * @param target The target argument to check for
     * @return true if the argument is present
     */
    private static boolean hasArgument(String[] args, String target) {
        if (args == null || target == null) {
            return false;
        }
        for (String arg : args) {
            if (arg != null && arg.equals(target)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Prints the help message
     */
    private static void printHelp() {
        System.out.println();
        System.out.println("MCJEBooster External Injector");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar MCJEBooster-26.1-05102026.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --auto     Automatically inject into the first Minecraft process found");
        System.out.println("  --force, -f  Skip confirmation prompts");
        System.out.println("  --help, -h   Display this help message");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  This tool attaches the MCJEBooster agent to a running Minecraft");
        System.out.println("  process to enable multi-core optimization.");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - Minecraft must be running");
        System.out.println("  - JDK (not just JRE) must be installed");
        System.out.println("  - Sufficient permissions to attach to the process");
        System.out.println();
    }
    
    /**
     * Gets the path to the agent JAR file
     * Attempts to locate the JAR in several ways:
     * 1. From the current working directory
     * 2. From the classpath
     * 3. From the location of the running JAR
     * 
     * @return The absolute path to the agent JAR, or null if not found
     */
    private static String getAgentPath() {
        // First, try to get the path from the current JAR
        try {
            String jarPath = InjectorMain.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            
            File jarFile = new File(jarPath);
            if (jarFile.exists()) {
                return jarFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Logger.warn("Could not determine JAR path from code source: " + e.getMessage());
        }
        
        // Try current working directory
        String[] possibleNames = {
            "MCJEBooster-26.1-05102026.jar",
            "MCJEBooster.jar",
            "mcjebooster.jar"
        };
        
        File cwd = new File(".");
        for (String name : possibleNames) {
            File candidate = new File(cwd, name);
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }
        
        return null;
    }
    
    /**
     * Finds all running Minecraft processes
     * Uses the Java Attach API to enumerate JVM processes
     * 
     * @return A list of Minecraft processes
     */
    private static List<MinecraftProcess> findMinecraftProcesses() {
        List<MinecraftProcess> processes = new java.util.ArrayList<>();
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        
        Logger.info("Scanning " + vms.size() + " JVM process(es)...");
        
        for (VirtualMachineDescriptor vmd : vms) {
            String displayName = vmd.displayName();
            String vmId = vmd.id();
            
            // Check if this is a Minecraft process
            if (isMinecraftProcess(displayName)) {
                MinecraftProcess mcProcess = new MinecraftProcess(vmId, displayName);
                processes.add(mcProcess);
                Logger.info("Found Minecraft process: PID=" + vmId + ", Name=" + displayName);
            }
        }
        
        return processes;
    }
    
    /**
     * Determines if a JVM process is a Minecraft process
     * Uses various heuristics to identify Minecraft
     * 
     * @param displayName The display name of the process
     * @return true if the process appears to be Minecraft
     */
    private static boolean isMinecraftProcess(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return false;
        }
        
        String lowerName = displayName.toLowerCase();
        
        // Check for common Minecraft indicators
        String[] indicators = {
            "net.minecraft.client.main.main",
            "net.minecraft.server",
            "minecraft",
            "mcp",
            "forge",
            "fabric",
            "optifine"
        };
        
        for (String indicator : indicators) {
            if (lowerName.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Selects the target process for injection
     * In auto mode, selects the first process. Otherwise, prompts the user.
     * 
     * @param processes The list of available processes
     * @return The selected process, or null if cancelled
     */
    private static MinecraftProcess selectTargetProcess(List<MinecraftProcess> processes) {
        if (autoMode) {
            Logger.info("Auto mode enabled - selecting first process");
            return processes.get(0);
        }
        
        if (processes.size() == 1) {
            Logger.info("Only one Minecraft process found - selecting it automatically");
            return processes.get(0);
        }
        
        // Multiple processes - let user choose
        System.out.println();
        System.out.println("Multiple Minecraft processes found:");
        System.out.println();
        
        for (int i = 0; i < processes.size(); i++) {
            MinecraftProcess p = processes.get(i);
            System.out.println("  [" + (i + 1) + "] PID: " + p.getPid() + " - " + p.getDisplayName());
        }
        
        System.out.println("  [0] Cancel");
        System.out.println();
        
        Scanner scanner = new Scanner(System.in);
        System.out.print("Select a process (0-" + processes.size() + "): ");
        
        try {
            int choice = scanner.nextInt();
            
            if (choice == 0) {
                return null;
            }
            
            if (choice < 1 || choice > processes.size()) {
                Logger.error("Invalid selection");
                return null;
            }
            
            return processes.get(choice - 1);
            
        } catch (Exception e) {
            Logger.error("Invalid input: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Prompts the user to confirm injection
     * 
     * @param target The target process
     * @return true if the user confirms
     */
    private static boolean confirmInjection(MinecraftProcess target) {
        System.out.println();
        System.out.println("About to inject MCJEBooster into:");
        System.out.println("  PID: " + target.getPid());
        System.out.println("  Process: " + target.getDisplayName());
        System.out.println();
        System.out.print("Continue? [Y/n]: ");
        
        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine().trim().toLowerCase();
        
        return response.isEmpty() || response.equals("y") || response.equals("yes");
    }
    
    /**
     * Injects the agent into the target process
     * 
     * @param target The target Minecraft process
     * @return true if injection was successful
     */
    private static boolean injectAgent(MinecraftProcess target) {
        Logger.info("Attaching to process PID=" + target.getPid() + "...");
        
        VirtualMachine vm = null;
        
        try {
            // Attach to the target JVM
            vm = VirtualMachine.attach(target.getPid());
            Logger.info("Successfully attached to target JVM");
            
            // Load the agent
            Logger.info("Loading agent...");
            vm.loadAgent(agentPath);
            Logger.info("Agent loaded successfully");
            
            return true;
            
        } catch (Exception e) {
            Logger.error("Injection failed: " + e.getMessage());
            
            // Provide helpful error messages for common issues
            if (e.getMessage().contains("AttachNotSupportedException")) {
                Logger.error("The target JVM does not support attachment.");
                Logger.error("This may be a JRE instead of JDK, or attachment may be disabled.");
            } else if (e.getMessage().contains("IOException")) {
                Logger.error("Communication error with target JVM.");
                Logger.error("The process may have terminated or be unresponsive.");
            } else if (e.getMessage().contains("AgentLoadException")) {
                Logger.error("Failed to load the agent JAR.");
                Logger.error("Ensure the agent JAR is valid and accessible.");
            }
            
            return false;
            
        } finally {
            // Always detach from the JVM
            if (vm != null) {
                try {
                    vm.detach();
                    Logger.info("Detached from target JVM");
                } catch (Exception e) {
                    Logger.warn("Error detaching from JVM: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Represents a Minecraft process
     */
    private static class MinecraftProcess {
        private final String pid;
        private final String displayName;
        
        public MinecraftProcess(String pid, String displayName) {
            this.pid = pid;
            this.displayName = displayName;
        }
        
        public String getPid() {
            return pid;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
