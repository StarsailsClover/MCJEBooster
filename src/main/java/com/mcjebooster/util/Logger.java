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

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logging utility for MCJEBooster
 * 
 * Provides leveled logging with timestamps and module identification.
 * This is a lightweight alternative to external logging frameworks
 * to minimize dependencies.
 * 
 * Log levels (in order of severity):
 * - DEBUG: Detailed debugging information
 * - INFO: General informational messages
 * - WARN: Warning messages for potential issues
 * - ERROR: Error messages for failures
 * 
 * @author StarsailsClover
 * @version 26.1-05102026
 * @since 1.0
 */
public class Logger {
    
    /** The log level - messages below this level are not printed */
    private static LogLevel currentLevel = LogLevel.INFO;
    
    /** The output stream for logging (defaults to System.out) */
    private static PrintStream outputStream = System.out;
    
    /** The error stream for error logging (defaults to System.err) */
    private static PrintStream errorStream = System.err;
    
    /** Whether to include timestamps in log messages */
    private static boolean includeTimestamps = true;
    
    /** Whether to include the module name in log messages */
    private static boolean includeModule = true;
    
    /** The module name to include in logs */
    private static String moduleName = "MCJEBooster";
    
    /** Date formatter for timestamps */
    private static final DateTimeFormatter timestampFormatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Enumeration of log levels
     */
    public enum LogLevel {
        DEBUG(0, "DEBUG"),
        INFO(1, "INFO"),
        WARN(2, "WARN"),
        ERROR(3, "ERROR");
        
        private final int level;
        private final String name;
        
        LogLevel(int level, String name) {
            this.level = level;
            this.name = name;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getName() {
            return name;
        }
    }
    
    /**
     * Sets the current log level
     * 
     * @param level The minimum level to log
     */
    public static void setLevel(LogLevel level) {
        currentLevel = level;
    }
    
    /**
     * Sets the log level from a string
     * 
     * @param levelName The level name (DEBUG, INFO, WARN, ERROR)
     */
    public static void setLevel(String levelName) {
        try {
            currentLevel = LogLevel.valueOf(levelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            warn("Invalid log level: " + levelName + ", using INFO");
            currentLevel = LogLevel.INFO;
        }
    }
    
    /**
     * Sets the output stream for logging
     * 
     * @param stream The output stream
     */
    public static void setOutputStream(PrintStream stream) {
        outputStream = stream;
    }
    
    /**
     * Sets the error stream for error logging
     * 
     * @param stream The error stream
     */
    public static void setErrorStream(PrintStream stream) {
        errorStream = stream;
    }
    
    /**
     * Sets whether to include timestamps in log messages
     * 
     * @param include true to include timestamps
     */
    public static void setIncludeTimestamps(boolean include) {
        includeTimestamps = include;
    }
    
    /**
     * Sets whether to include the module name in log messages
     * 
     * @param include true to include module name
     */
    public static void setIncludeModule(boolean include) {
        includeModule = include;
    }
    
    /**
     * Sets the module name for log messages
     * 
     * @param name The module name
     */
    public static void setModuleName(String name) {
        moduleName = name;
    }
    
    /**
     * Logs a debug message
     * 
     * @param message The message to log
     */
    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }
    
    /**
     * Logs a debug message with formatting
     * 
     * @param format The format string
     * @param args The format arguments
     */
    public static void debug(String format, Object... args) {
        debug(String.format(format, args));
    }
    
    /**
     * Logs an informational message
     * 
     * @param message The message to log
     */
    public static void info(String message) {
        log(LogLevel.INFO, message);
    }
    
    /**
     * Logs an informational message with formatting
     * 
     * @param format The format string
     * @param args The format arguments
     */
    public static void info(String format, Object... args) {
        info(String.format(format, args));
    }
    
    /**
     * Logs a warning message
     * 
     * @param message The warning message
     */
    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }
    
    /**
     * Logs a warning message with formatting
     * 
     * @param format The format string
     * @param args The format arguments
     */
    public static void warn(String format, Object... args) {
        warn(String.format(format, args));
    }
    
    /**
     * Logs an error message
     * 
     * @param message The message to log
     */
    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }
    
    /**
     * Logs an error message with formatting
     * 
     * @param format The format string
     * @param args The format arguments
     */
    public static void error(String format, Object... args) {
        error(String.format(format, args));
    }
    
    /**
     * Logs an exception with an error message
     * 
     * @param message The error message
     * @param e The exception
     */
    public static void error(String message, Throwable e) {
        error(message);
        if (e != null) {
            error("Exception: " + e.getClass().getName() + ": " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                error("  at " + element.toString());
            }
        }
    }
    
    /**
     * Core logging method
     * 
     * @param level The log level
     * @param message The message to log
     */
    private static void log(LogLevel level, String message) {
        if (level.getLevel() < currentLevel.getLevel()) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Add timestamp
        if (includeTimestamps) {
            sb.append("[");
            sb.append(LocalDateTime.now().format(timestampFormatter));
            sb.append("] ");
        }
        
        // Add module name
        if (includeModule) {
            sb.append("[");
            sb.append(moduleName);
            sb.append("] ");
        }
        
        // Add log level
        sb.append("[");
        sb.append(level.getName());
        sb.append("] ");
        
        // Add message
        sb.append(message);
        
        // Output to appropriate stream
        String logLine = sb.toString();
        
        if (level == LogLevel.ERROR) {
            errorStream.println(logLine);
        } else {
            outputStream.println(logLine);
        }
    }
    
    /**
     * Gets the current log level
     * 
     * @return The current log level
     */
    public static LogLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Checks if debug logging is enabled
     * 
     * @return true if debug level is enabled
     */
    public static boolean isDebugEnabled() {
        return currentLevel.getLevel() <= LogLevel.DEBUG.getLevel();
    }
    
    /**
     * Checks if the specified level is enabled
     * 
     * @param level The level to check
     * @return true if the level is enabled
     */
    public static boolean isEnabled(LogLevel level) {
        return currentLevel.getLevel() <= level.getLevel();
    }
}
