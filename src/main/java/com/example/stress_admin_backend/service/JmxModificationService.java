package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.UseCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JmxModificationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Modifies a JMX file with the thread group and server configuration from the use case
     */
    public String modifyJmxWithConfiguration(String jmxPath, UseCase useCase, int durationSeconds) throws IOException {
        // Read the original JMX file
        String jmxContent = Files.readString(Paths.get(jmxPath));
        
        System.out.println("Starting JMX modification for use case: " + useCase.getName());
        System.out.println("Thread group config: " + useCase.getThreadGroupConfig());
        System.out.println("Server config: " + useCase.getServerConfig());
        
        // Update CSV Data Set Config filename to use server storage path
        System.out.println("Updating CSV Data Set Config filename...");
        jmxContent = updateCsvDataSetConfigFilename(jmxContent, useCase);
        System.out.println("Updated CSV filename to server storage path");
        
        // Apply thread group configuration if available
        if (useCase.getThreadGroupConfig() != null && !useCase.getThreadGroupConfig().isEmpty()) {
            System.out.println("Applying thread group configuration...");
            jmxContent = applyThreadGroupConfiguration(jmxContent, useCase.getThreadGroupConfig());
        } else {
            System.out.println("No thread group configuration to apply");
        }
        
        // Apply server configuration if available
        if (useCase.getServerConfig() != null && !useCase.getServerConfig().isEmpty()) {
            System.out.println("Applying server configuration...");
            jmxContent = applyServerConfiguration(jmxContent, useCase.getServerConfig());
        } else {
            System.out.println("No server configuration to apply");
        }
        
        System.out.println("JMX modification completed");
        return jmxContent;
    }

    /**
     * Applies thread group configuration to JMX content
     */
    private String applyThreadGroupConfiguration(String jmxContent, String threadGroupConfigJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(threadGroupConfigJson, new TypeReference<Map<String, Object>>() {});
            
            System.out.println("Applying thread group configuration: " + config);
            
            // Update number of threads
            if (config.containsKey("numberOfThreads")) {
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.num_threads", config.get("numberOfThreads").toString());
                System.out.println("Updated numberOfThreads to: " + config.get("numberOfThreads"));
            }
            
            // Update ramp-up period
            if (config.containsKey("rampUpPeriod")) {
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.ramp_time", config.get("rampUpPeriod").toString());
                System.out.println("Updated rampUpPeriod to: " + config.get("rampUpPeriod"));
            }
            
            // Update loop count and infinite loop setting - handle them together
            boolean infiniteLoop = false;
            if (config.containsKey("infiniteLoop")) {
                infiniteLoop = Boolean.parseBoolean(config.get("infiniteLoop").toString());
                System.out.println("Processing infiniteLoop: " + infiniteLoop);
            }
            
            if (infiniteLoop) {
                // For infinite loops, set loops to -1 and continue_forever to true
                jmxContent = updateJmxProperty(jmxContent, "LoopController.loops", "-1");
                jmxContent = updateJmxProperty(jmxContent, "LoopController.continue_forever", "true");
                System.out.println("Set infinite loop: loops=-1, continue_forever=true");
            } else {
                // For finite loops, use the actual loop count and set continue_forever to false
                String loopCount = "1"; // default value
                if (config.containsKey("loopCount")) {
                    loopCount = config.get("loopCount").toString();
                }
                jmxContent = updateJmxProperty(jmxContent, "LoopController.loops", loopCount);
                jmxContent = updateJmxProperty(jmxContent, "LoopController.continue_forever", "false");
                System.out.println("Set finite loop: loops=" + loopCount + ", continue_forever=false");
            }
            
            // Update same user on each iteration
            if (config.containsKey("sameUserOnEachIteration")) {
                boolean sameUser = Boolean.parseBoolean(config.get("sameUserOnEachIteration").toString());
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.same_user_on_next_iteration", sameUser ? "true" : "false");
                System.out.println("Updated sameUserOnEachIteration to: " + sameUser);
            }
            
            // Update delay thread creation
            if (config.containsKey("delayThreadCreation")) {
                boolean delayCreation = Boolean.parseBoolean(config.get("delayThreadCreation").toString());
                System.out.println("Processing delayThreadCreation: " + delayCreation);
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.delayedStart", delayCreation ? "true" : "false");
                System.out.println("Updated delayThreadCreation to: " + delayCreation);
            }
            
            // Check if duration or startup delay is configured
            boolean hasDurationOrDelay = config.containsKey("duration") || config.containsKey("startupDelay");
            System.out.println("DEBUG: hasDurationOrDelay = " + hasDurationOrDelay);
            System.out.println("DEBUG: config contains duration = " + config.containsKey("duration"));
            System.out.println("DEBUG: config contains startupDelay = " + config.containsKey("startupDelay"));
            System.out.println("DEBUG: config contains specifyThreadLifetime = " + config.containsKey("specifyThreadLifetime"));
            
            // Always enable scheduler if duration or startup delay is configured
            // This is critical to prevent infinite runtime
            if (hasDurationOrDelay) {
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.scheduler", "true");
                System.out.println("CRITICAL: Enabled ThreadGroup.scheduler to prevent infinite runtime - duration/startup delay configured");
                
                // Double-check: verify the scheduler was actually set
                if (!jmxContent.contains("<boolProp name=\"ThreadGroup.scheduler\">true</boolProp>")) {
                    // Force add scheduler property if it wasn't set
                    jmxContent = addPropertyToJmx(jmxContent, "ThreadGroup.scheduler", "true");
                    System.out.println("FORCE ADDED: ThreadGroup.scheduler=true property");
                }
            } else if (config.containsKey("specifyThreadLifetime")) {
                // Only use UI setting if no duration/delay is specified
                boolean specifyLifetime = Boolean.parseBoolean(config.get("specifyThreadLifetime").toString());
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.scheduler", specifyLifetime ? "true" : "false");
                System.out.println("Updated specifyThreadLifetime to: " + specifyLifetime);
            }
            
            
            // Update duration from UI Thread Group config (overrides JMX file duration)
            if (config.containsKey("duration")) {
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.duration", config.get("duration").toString());
                System.out.println("Updated duration to: " + config.get("duration") + " seconds (from UI config)");
            }
            
            // Update startup delay
            if (config.containsKey("startupDelay")) {
                String startupDelay = config.get("startupDelay").toString();
                System.out.println("Processing startupDelay: " + startupDelay);
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.delay", startupDelay);
                System.out.println("Updated startupDelay to: " + startupDelay);
            }
            
            // Update action after sampler error
            if (config.containsKey("actionAfterSamplerError")) {
                String action = config.get("actionAfterSamplerError").toString();
                String actionValue = getActionAfterSamplerErrorValue(action);
                jmxContent = updateJmxProperty(jmxContent, "ThreadGroup.on_sample_error", actionValue);
                System.out.println("Updated actionAfterSamplerError to: " + actionValue);
            }
            
        } catch (Exception e) {
            System.err.println("Error applying thread group configuration: " + e.getMessage());
            e.printStackTrace();
        }
        
        return jmxContent;
    }

    /**
     * Applies server configuration to JMX content
     */
    private String applyServerConfiguration(String jmxContent, String serverConfigJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(serverConfigJson, new TypeReference<Map<String, Object>>() {});
            
            System.out.println("Applying server configuration: " + config);
            
            // Update HTTP Request Defaults (if they exist)
            // This will update the default server, port, and protocol for all HTTP requests
            
            // Update server name/IP in HTTP Request Defaults
            if (config.containsKey("server")) {
                String server = config.get("server").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.domain", server);
                System.out.println("Updated HTTPSampler.domain to: " + server);
            }
            
            // Update port in HTTP Request Defaults
            if (config.containsKey("port")) {
                String port = config.get("port").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.port", port);
                System.out.println("Updated HTTPSampler.port to: " + port);
            }
            
            // Update protocol in HTTP Request Defaults
            if (config.containsKey("protocol")) {
                String protocol = config.get("protocol").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.protocol", protocol);
                System.out.println("Updated HTTPSampler.protocol to: " + protocol);
            }
            
            // Also try alternative property names that might be used in different JMX versions
            if (config.containsKey("server")) {
                String server = config.get("server").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.serverName", server);
                System.out.println("Updated HTTPSampler.serverName to: " + server);
            }
            
            if (config.containsKey("port")) {
                String port = config.get("port").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.portNumber", port);
                System.out.println("Updated HTTPSampler.portNumber to: " + port);
            }
            
            if (config.containsKey("protocol")) {
                String protocol = config.get("protocol").toString();
                jmxContent = updateJmxProperty(jmxContent, "HTTPSampler.protocolType", protocol);
                System.out.println("Updated HTTPSampler.protocolType to: " + protocol);
            }
            
        } catch (Exception e) {
            System.err.println("Error applying server configuration: " + e.getMessage());
            e.printStackTrace();
        }
        
        return jmxContent;
    }
    
    /**
     * Updates a property value in JMX content using regex, or adds it if it doesn't exist
     */
    private String updateJmxProperty(String jmxContent, String propertyName, String newValue) {
        // Pattern to match the property in JMX format: <stringProp name="propertyName">oldValue</stringProp>
        String pattern = "<stringProp name=\"" + Pattern.quote(propertyName) + "\">([^<]*)</stringProp>";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(jmxContent);
        
        if (matcher.find()) {
            return jmxContent.replaceAll(pattern, "<stringProp name=\"" + propertyName + "\">" + newValue + "</stringProp>");
        }
        
        // Also try boolean properties
        String boolPattern = "<boolProp name=\"" + Pattern.quote(propertyName) + "\">([^<]*)</boolProp>";
        Pattern boolRegex = Pattern.compile(boolPattern);
        Matcher boolMatcher = boolRegex.matcher(jmxContent);
        
        if (boolMatcher.find()) {
            return jmxContent.replaceAll(boolPattern, "<boolProp name=\"" + propertyName + "\">" + newValue + "</boolProp>");
        }
        
        // Also try int properties
        String intPattern = "<intProp name=\"" + Pattern.quote(propertyName) + "\">([^<]*)</intProp>";
        Pattern intRegex = Pattern.compile(intPattern);
        Matcher intMatcher = intRegex.matcher(jmxContent);
        
        if (intMatcher.find()) {
            return jmxContent.replaceAll(intPattern, "<intProp name=\"" + propertyName + "\">" + newValue + "</intProp>");
        }
        
        // Also try long properties
        String longPattern = "<longProp name=\"" + Pattern.quote(propertyName) + "\">([^<]*)</longProp>";
        Pattern longRegex = Pattern.compile(longPattern);
        Matcher longMatcher = longRegex.matcher(jmxContent);
        
        if (longMatcher.find()) {
            return jmxContent.replaceAll(longPattern, "<longProp name=\"" + propertyName + "\">" + newValue + "</longProp>");
        }
        
        // If property doesn't exist, add it before the closing ThreadGroup tag
        System.out.println("Property " + propertyName + " not found, adding it to JMX");
        return addPropertyToJmx(jmxContent, propertyName, newValue);
    }
    
    /**
     * Adds a new property to the JMX content before the closing ThreadGroup tag
     */
    private String addPropertyToJmx(String jmxContent, String propertyName, String newValue) {
        System.out.println("Adding new property: " + propertyName + " = " + newValue);
        
        // Determine the property type based on the property name and value
        String propertyTag;
        if (propertyName.equals("ThreadGroup.delayedStart") || 
            propertyName.equals("ThreadGroup.scheduler") || 
            propertyName.equals("LoopController.continue_forever") ||
            propertyName.equals("ThreadGroup.same_user_on_next_iteration")) {
            // Boolean properties
            propertyTag = "    <boolProp name=\"" + propertyName + "\">" + newValue + "</boolProp>\n";
            System.out.println("Adding boolean property: " + propertyTag.trim());
        } else if (propertyName.equals("ThreadGroup.num_threads") || 
                   propertyName.equals("ThreadGroup.ramp_time")) {
            // Integer properties
            propertyTag = "    <intProp name=\"" + propertyName + "\">" + newValue + "</intProp>\n";
            System.out.println("Adding integer property: " + propertyTag.trim());
        } else if (propertyName.equals("ThreadGroup.duration")) {
            // Long properties
            propertyTag = "    <longProp name=\"" + propertyName + "\">" + newValue + "</longProp>\n";
            System.out.println("Adding long property: " + propertyTag.trim());
        } else {
            // String properties (default)
            propertyTag = "    <stringProp name=\"" + propertyName + "\">" + newValue + "</stringProp>\n";
            System.out.println("Adding string property: " + propertyTag.trim());
        }
        
        // Find the closing ThreadGroup tag and insert the property before it
        String threadGroupClosePattern = "</ThreadGroup>";
        String result = jmxContent.replace(threadGroupClosePattern, propertyTag + threadGroupClosePattern);
        System.out.println("Property added successfully");
        return result;
    }

    /**
     * Converts action after sampler error text to JMX value
     */
    private String getActionAfterSamplerErrorValue(String action) {
        switch (action) {
            case "Continue":
                return "continue";
            case "Start Next Thread Loop":
                return "startnextloop";
            case "Stop Thread":
                return "stopthread";
            case "Stop Test":
                return "stoptest";
            case "Stop Test Now":
                return "stoptestnow";
            default:
                return "continue";
        }
    }

    /**
     * Updates CSV Data Set Config filename to use server storage path
     */
    private String updateCsvDataSetConfigFilename(String jmxContent, UseCase useCase) {
        // Extract CSV filename from use case
        String csvFilename = useCase.getCsvPath();
        if (csvFilename == null || csvFilename.isEmpty()) {
            System.out.println("No CSV path found in use case, skipping CSV filename update");
            return jmxContent;
        }
        
        // Extract just the filename from the full path
        String csvFileName = Paths.get(csvFilename).getFileName().toString();
        String serverCsvPath = "/home/ubuntu/stress-admin-storage/csv/" + csvFileName;
        
        System.out.println("Original CSV path: " + csvFilename);
        System.out.println("Server CSV path: " + serverCsvPath);
        
        // Debug: Check if JMX contains CSV Data Set Config
        boolean containsCsvDataSet = jmxContent.contains("CSVDataSet");
        System.out.println("JMX contains CSVDataSet: " + containsCsvDataSet);
        
        if (containsCsvDataSet) {
            // Debug: Find all filename properties
            Pattern filenamePattern = Pattern.compile("<stringProp name=\"filename\">(.*?)</stringProp>", Pattern.DOTALL);
            java.util.regex.Matcher filenameMatcher = filenamePattern.matcher(jmxContent);
            int filenameCount = 0;
            while (filenameMatcher.find()) {
                filenameCount++;
                String foundPath = filenameMatcher.group(1);
                System.out.println("Found filename property #" + filenameCount + ": " + foundPath);
                
                // Check if this looks like a Windows CSV path
                if (foundPath.toLowerCase().contains(".csv") && (foundPath.contains("\\") || foundPath.contains("C:/"))) {
                    System.out.println("  -> This looks like a Windows CSV path that needs updating!");
                }
            }
            System.out.println("Total filename properties found: " + filenameCount);
            
            // Debug: Show a snippet of the JMX around CSVDataSet
            int csvDataSetIndex = jmxContent.indexOf("CSVDataSet");
            if (csvDataSetIndex >= 0) {
                String snippet = jmxContent.substring(Math.max(0, csvDataSetIndex - 200), 
                    Math.min(jmxContent.length(), csvDataSetIndex + 500));
                System.out.println("JMX snippet around CSVDataSet:");
                System.out.println(snippet);
            }
        }
        
        // FIXED: Use a single, precise pattern to avoid XML corruption
        String result = jmxContent;
        boolean updated = false;
        
        // Single pattern: Look specifically for CSVDataSet filename property
        // This pattern is more precise and won't interfere with other XML elements
        String csvDataSetPattern = "(<CSVDataSet[^>]*>.*?<stringProp name=\"filename\">)([^<]*)(</stringProp>.*?</CSVDataSet>)";
        Pattern pattern = Pattern.compile(csvDataSetPattern, Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);
        
        if (matcher.find()) {
            String originalPath = matcher.group(2);
            System.out.println("Found CSVDataSet with filename: " + originalPath);
            
            // Always replace with the server path (unless it's already the correct server path)
            if (!originalPath.equals(serverCsvPath)) {
                result = matcher.replaceAll("$1" + serverCsvPath + "$3");
                updated = true;
                System.out.println("✅ Updated CSV filename using precise CSVDataSet pattern");
                System.out.println("   Original: " + originalPath);
                System.out.println("   Updated: " + serverCsvPath);
            } else {
                System.out.println("ℹ️ CSV path is already the correct server path, no update needed");
            }
        } else {
            System.out.println("⚠️ No CSVDataSet element found with filename property");
        }
        
        if (updated) {
            System.out.println("✅ Successfully updated CSV Data Set Config filename to: " + serverCsvPath);
        } else {
            System.out.println("ℹ️ No CSV Data Set Config found to update or already correct");
        }
        
        return result;
    }
}
