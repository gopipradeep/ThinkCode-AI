package com.thinkcode.ai.onlinecompiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class InteractiveCodeExecutionHandler extends TextWebSocketHandler {
    
    private static final Map<String, Process> processes = new ConcurrentHashMap<>();
    private static final Map<String, PrintWriter> writers = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionLanguages = new ConcurrentHashMap<>();
    // Tracks if we have already sent an input request to the user for this session
    private static final Map<String, AtomicBoolean> inputRequestedFlags = new ConcurrentHashMap<>(); 
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Inner class for reliable output streaming
    private class StreamGobbler implements Runnable {
        private final WebSocketSession session;
        private final InputStream inputStream;
        private final String messageType;
        private final String sessionId;
        private final String language;
        private volatile boolean running = true;
        private long lastOutputTime = System.currentTimeMillis();
        
        public StreamGobbler(WebSocketSession session, InputStream inputStream, String messageType, String sessionId, String language) {
            this.session = session;
            this.inputStream = inputStream;
            this.messageType = messageType;
            this.sessionId = sessionId;
            this.language = language;
        }

        public void stopRunning() {
            this.running = false;
        }

        @Override
        public void run() {
            // Using InputStreamReader directly (not BufferedReader) and a small buffer for immediate streaming
            try (Reader reader = new InputStreamReader(inputStream)) {
                char[] buffer = new char[512]; 
                int bytesRead;

                while (running) {
                    if (reader.ready()) {
                        // Blocking read will return when data is available or stream ends
                         bytesRead = reader.read(buffer);
                        if (bytesRead > 0) {
                            String output = new String(buffer, 0, bytesRead);
                            sendMessage(session, "output", output);
                            lastOutputTime = System.currentTimeMillis(); // Reset activity time on output
                            // If any output is received, program is not waiting for input
                            inputRequestedFlags.computeIfPresent(sessionId, (k, v) -> { v.set(false); return v; });
                        } else if (bytesRead == -1) {
                            // End of Stream (EOF)
                            break; 
                        }
                    } else {
                        // Logic to detect if a long-running process might be waiting for input.
                        Process process = processes.get(sessionId);
                        AtomicBoolean inputRequested = inputRequestedFlags.get(sessionId);
                        
                        if (process != null && process.isAlive() && inputRequested != null && !inputRequested.get()) {
                            long timeSinceLastOutput = System.currentTimeMillis() - lastOutputTime;
                            int timeout = getTimeoutForLanguage(language);
                            
                            // If no output for a period (timeout), assume program is blocked and ask for input
                            if (timeSinceLastOutput > timeout) {
                                sendMessage(session, "input_request", "");
                                inputRequested.set(true); 
                                System.out.println("⏳ Input requested for " + language + " on " + sessionId);
                            }
                        }
                        // Small sleep to yield to other threads and prevent aggressive CPU polling
                        Thread.sleep(5); 
                    }
                }
                
                // CRITICAL FIX: After the 'running' flag is set to false (or stream ends),
                // aggressively drain the remaining buffer to catch final error messages.
                while (reader.ready()) {
                    bytesRead = reader.read(buffer);
                    if (bytesRead > 0) {
                        String output = new String(buffer, 0, bytesRead);
                        sendMessage(session, "output", output);
                    } else if (bytesRead == -1) {
                        break;
                    }
                }
                
            } catch (IOException e) {
                // Ignore IOException if process died (expected behavior)
            } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("❌ StreamGobbler unexpected error: " + e.getMessage());
            } finally {
                // Ensure the stream is closed if running loop stops
                try { 
                    if (inputStream != null) inputStream.close(); 
                } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private int getTimeoutForLanguage(String language) {
        // Adjust timeouts for the new, more robust input detection logic (in milliseconds)
        switch (language) {
            case "c":
            case "go":
            case "ruby":
                return 300; 
            case "javascript":
                return 400; 
            case "python":
                return 350;
            case "java":
            case "kotlin":
                return 500; 
            default:
                return 450; 
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("✅ WebSocket Connected: " + session.getId());
        // Do not send connection message, rely on the frontend detecting connection status
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        String sessionId = session.getId();
        
        // System.out.println("📥 Received: " + type + " from " + sessionId);
        
        switch (type) {
            case "execute":
                String language = node.get("language").asText();
                String code = node.get("code").asText();
                
                killProcess(sessionId);
                new Thread(() -> executeUniversal(session, language, code, sessionId)).start();
                break;
                
            case "input":
                String inputData = node.get("data").asText();
                handleInputUniversal(sessionId, inputData, session);
                // Clear the input requested flag immediately after receiving input
                inputRequestedFlags.computeIfPresent(sessionId, (k, v) -> { v.set(false); return v; });
                break;
                
            case "stop":
                killProcess(sessionId);
                sendMessage(session, "execution_complete", "Execution stopped");
                break;
                
            case "ping":
                // Send pong response if ping is received (Keep-alive)
                sendMessage(session, "pong", "Server alive");
                break;
                
            default:
                // Ignore unknown message types
                break;
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // FIX: Retrieve sessionId from the session object
        String sessionId = session.getId(); 
        System.out.println("🔌 WebSocket Disconnected: " + sessionId + " - " + status);
        killProcess(sessionId);
        sessionLanguages.remove(sessionId);
        inputRequestedFlags.remove(sessionId);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("❌ WebSocket Transport Error: " + exception.getMessage());
    }
    
    // =============================================================
    // Compiler Execution Methods
    // =============================================================
    
    private void executeUniversal(WebSocketSession session, String language, String code, String sessionId) {
        Path tempDir = null;
        Process process = null;
        StreamGobbler stdoutGobbler = null;
        StreamGobbler stderrGobbler = null;
        
        try {
            System.out.println("🚀 Starting CLEAN-11-LANGUAGES execution for " + sessionId + " - Language: " + language);
            
            sessionLanguages.put(sessionId, language);
            inputRequestedFlags.put(sessionId, new AtomicBoolean(false));
            sendMessage(session, "execution_started", ""); // Signal successful compile/start
            
            tempDir = Files.createTempDirectory("clean_11_lang_exec_" + sessionId + "_");
            // System.out.println("📁 Created temp dir: " + tempDir);
            
            String[] command = getCommand(language, code, tempDir, session, sessionId); // Pass session and sessionId
            if (command == null) {
                sendMessage(session, "error", "Unsupported language: " + language);
                return;
            }
            
            // System.out.println("🔧 Command: " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            
            Map<String, String> env = pb.environment();
            // Essential environment variables for unbuffered/clean execution
            env.put("PYTHONUNBUFFERED", "1");
            env.put("NODE_NO_READLINE", "1");
            env.put("TERM", "dumb");
            env.put("LC_ALL", "C");
            env.put("LANG", "C");
            
            // Enhanced environment setup
            if ("javascript".equals(language)) {
                env.put("NODE_NO_WARNINGS", "1");
                env.put("NODE_OPTIONS", "--no-deprecation");
                env.put("NODE_DISABLE_COLORS", "1");
            }
            if ("php".equals(language)) {
                env.put("XDEBUG_MODE", "off");
            }
            if ("c".equals(language) || "ruby".equals(language)) {
                env.put("SETVBUF", "0");
                env.put("RUBY_DISABLE_GEMS", "1");
                // FIX: Added explicit environment setting for Ruby encoding
                env.put("RUBYOPT", "-EUTF-8:UTF-8");
            }
            
            process = pb.start();
            
            processes.put(sessionId, process);
            PrintWriter writer = new PrintWriter(process.getOutputStream(), false); 
            writers.put(sessionId, writer);
            
            // Start output gobblers for reliable streaming
            stdoutGobbler = new StreamGobbler(session, process.getInputStream(), "output", sessionId, language);
            stderrGobbler = new StreamGobbler(session, process.getErrorStream(), "output", sessionId, language);
            new Thread(stdoutGobbler).start();
            new Thread(stderrGobbler).start();
            
            // Wait for process completion
            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5-minute timeout
            
            // Stop the gobblers immediately after process terminates or times out
            if (stdoutGobbler != null) stdoutGobbler.stopRunning();
            if (stderrGobbler != null) stderrGobbler.stopRunning();

            // Give the gobblers a brief moment to drain any remaining output/errors
            Thread.sleep(75); 
            
            int exitCode = -1;

            if (finished) {
                exitCode = process.exitValue();
                
                if (exitCode != 0) {
                    System.out.println("❌ Process failed - Exit code: " + exitCode + ". Checking remaining error output...");
                } else {
                    System.out.println("✅ Process completed - Exit code: " + exitCode);
                }
                
                // Send final completion status and exit code
                sendMessage(session, "execution_complete", "Exit code: " + exitCode);
            } else {
                System.out.println("⏰ Process timeout - Force killing");
                process.destroyForcibly();
                sendMessage(session, "error", "Execution timeout (5 minutes)");
                // Send execution_complete after error message
                sendMessage(session, "execution_complete", "Exit code: 124 (Timeout)");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Execution error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(session, "error", "Execution failed: " + e.getMessage());
            sendMessage(session, "execution_complete", "Exit code: 1");
        } finally {
            // Ensure gobblers are stopped even if an exception occurred earlier
            if (stdoutGobbler != null) stdoutGobbler.stopRunning();
            if (stderrGobbler != null) stderrGobbler.stopRunning();
            
            cleanupUniversal(sessionId, tempDir);
        }
    }
    
    private void handleInputUniversal(String sessionId, String inputData, WebSocketSession session) {
        Process process = processes.get(sessionId);
        PrintWriter writer = writers.get(sessionId);
        String language = sessionLanguages.getOrDefault(sessionId, "unknown");
        
        if (process != null && process.isAlive() && writer != null) {
            try {
                // CRITICAL FIX: Send the newline character for console-based input
                writer.println(inputData);
                writer.flush();
                
                // Set input flag to false since we just sent input
                inputRequestedFlags.computeIfPresent(sessionId, (k, v) -> { v.set(false); return v; });
                
                // FIX: Increased synchronization delay to 150ms for reliable prompt display
                // This gives the executed process time to print the next prompt and for the gobbler to read it.
                if ("c".equals(language) || "ruby".equals(language) || "go".equals(language) || "python".equals(language) || "java".equals(language)) {
                    Thread.sleep(150); 
                } else {
                    Thread.sleep(10); // Default small delay for other languages
                }
                
            } catch (Exception e) {
                sendMessage(session, "error", "Failed to send input: " + e.getMessage());
            }
        } else {
            String errorMsg = String.format("No active process to send input to.");
            sendMessage(session, "error", errorMsg);
        }
    }
    
    // FIX: Added session and sessionId parameters for use in error/output streaming during compilation steps.
    private String[] getCommand(String language, String code, Path tempDir, WebSocketSession session, String sessionId) throws Exception {
        
        Process compile = null; 
        
        switch (language) {
            case "python":
                Path pyFile = tempDir.resolve("main.py");
                Files.writeString(pyFile, code);
                // -u forces stdout and stderr to be totally unbuffered
                return new String[]{"python3", "-u", pyFile.toString()};
                
            case "java":
                // 1. Detect package name and Main class name dynamically
                String packageName = null;
                String mainClassName = "Main";
                
                String[] javaLines = code.split("\n");
                for (String line : javaLines) {
                    if (line.trim().startsWith("package ")) {
                        String packageLine = line.trim();
                        int semiIndex = packageLine.indexOf(';');
                        if (semiIndex != -1) {
                            packageName = packageLine.substring(8, semiIndex).trim();
                            break;
                        }
                    }
                }
                
                // Attempt to find the class name containing the entry point (public static void main)
                // This is a robust heuristic: find the first class name that contains 'main'
                // FIX: Updated regex to accept 'class' or 'public class'
                String javaClassNameRegex = "(?:public\\s+)?class\\s+(\\w+)"; 
                java.util.regex.Pattern javaPattern = java.util.regex.Pattern.compile(javaClassNameRegex);
                
                for (String line : javaLines) {
                    java.util.regex.Matcher matcher = javaPattern.matcher(line);
                    if (matcher.find()) {
                        String detectedClassName = matcher.group(1);
                        // Check if this class contains the static void main signature (simple check)
                        if (code.contains("public static void main")) {
                            mainClassName = detectedClassName;
                            break;
                        }
                    }
                }
                
                // Dynamic Java Filename based on detected class name
                Path javaFile = tempDir.resolve(mainClassName + ".java");
                Files.writeString(javaFile, code);

                if (packageName != null && !packageName.isEmpty()) {
                    mainClassName = packageName + "." + mainClassName;
                }
                
                // 2. Compilation command: Use -d . to output classes into package structure
                ProcessBuilder javac = new ProcessBuilder("javac", "-d", ".", javaFile.getFileName().toString());
                javac.directory(tempDir.toFile());
                compile = javac.start();
                boolean compileFinished = compile.waitFor(15, TimeUnit.SECONDS);
                
                if (!compileFinished || compile.exitValue() != 0) {
                    String error = getErrorOutput(compile.getErrorStream());
                    throw new Exception("Java compilation failed: " + error);
                }
                
                // 3. Execution command: Use the fully qualified class name
                return new String[]{"java", "-cp", tempDir.toString(), mainClassName};
                
            case "cpp":
                Path cppFile = tempDir.resolve("main.cpp");
                Files.writeString(cppFile, code);
                
                ProcessBuilder gpp = new ProcessBuilder("g++", "-std=c++17", "-O2", cppFile.toString(), "-o", tempDir.resolve("main").toString());
                gpp.directory(tempDir.toFile());
                Process cppCompile = gpp.start();
                boolean cppFinished = cppCompile.waitFor(15, TimeUnit.SECONDS);
                
                if (!cppFinished || cppCompile.exitValue() != 0) {
                    String error = getErrorOutput(cppCompile.getErrorStream());
                    throw new Exception("C++ compilation failed: " + error);
                }
                
                return new String[]{tempDir.resolve("main").toString()};
                
            case "kotlin":
                Path ktFile = tempDir.resolve("Main.kt");
                Files.writeString(ktFile, code);
                
                ProcessBuilder kotlinc = new ProcessBuilder("kotlinc", ktFile.toString(), "-include-runtime", "-d", tempDir.resolve("main.jar").toString());
                kotlinc.directory(tempDir.toFile());
                Process ktCompile = kotlinc.start();
                boolean ktFinished = ktCompile.waitFor(20, TimeUnit.SECONDS);
                
                if (!ktFinished || ktCompile.exitValue() != 0) {
                    String error = getErrorOutput(ktCompile.getErrorStream());
                    throw new Exception("Kotlin compilation failed: " + error);
                }
                
                return new String[]{"java", "-jar", tempDir.resolve("main.jar").toString()};
                
            case "php":
                Path phpFile = tempDir.resolve("main.php");
                Files.writeString(phpFile, code);
                // FIX: Reverted to minimal execution flags
                return new String[]{"php", "-n", "-d", "display_errors=1", "-d", "error_reporting=E_ALL", phpFile.toString()};
                
            case "rust":
                Path rustFile = tempDir.resolve("main.rs");
                Files.writeString(rustFile, code);
                
                ProcessBuilder rustc = new ProcessBuilder("rustc", rustFile.toString(), "-o", tempDir.resolve("main").toString());
                rustc.directory(tempDir.toFile());
                Process rustCompile = rustc.start();
                boolean rustFinished = rustCompile.waitFor(30, TimeUnit.SECONDS);
                
                if (!rustFinished || rustCompile.exitValue() != 0) {
                    String error = getErrorOutput(rustCompile.getErrorStream());
                    throw new Exception("Rust compilation failed: " + error);
                }
                
                return new String[]{tempDir.resolve("main").toString()};
                
            case "go":
                Path goFile = tempDir.resolve("main.go");
                Files.writeString(goFile, code);
                return new String[]{"go", "run", goFile.toString()};
                
            case "c":
                Path cFile = tempDir.resolve("main.c");
                Files.writeString(cFile, code);
                
                // Using -std=c99 
                ProcessBuilder gcc = new ProcessBuilder("gcc", "-std=c99", cFile.toString(), "-o", tempDir.resolve("main").toString());
                gcc.directory(tempDir.toFile());
                Process cCompile = gcc.start();
                boolean cFinished = cCompile.waitFor(15, TimeUnit.SECONDS);
                
                if (!cFinished || cCompile.exitValue() != 0) {
                    String error = getErrorOutput(compile.getErrorStream());
                    throw new Exception("C compilation failed: " + error);
                }
                
                return new String[]{tempDir.resolve("main").toString()};
                
            case "csharp":
                // 1. Detect Main Class Name
                String csMainClassName = "Program"; // Default
                
                // Attempt to find the first public class name
                String csClassNameRegex = "public\\s+class\\s+(\\w+)";
                java.util.regex.Pattern csPattern = java.util.regex.Pattern.compile(csClassNameRegex);
                
                for (String line : code.split("\n")) {
                    java.util.regex.Matcher matcher = csPattern.matcher(line);
                    if (matcher.find()) {
                        csMainClassName = matcher.group(1);
                        break;
                    }
                }
                
                // Dynamic C# Filename based on detected class name
                Path csFile = tempDir.resolve(csMainClassName + ".cs");
                Files.writeString(csFile, code);
                
                try {
                    // Create a robust .csproj file targeting the latest stable runtime (net8.0)
                    String csprojBuild = String.format("""
                        <Project Sdk="Microsoft.NET.Sdk">
                          <PropertyGroup>
                            <OutputType>Exe</OutputType>
                            <TargetFramework>net8.0</TargetFramework> 
                            <ImplicitUsings>enable</ImplicitUsings>
                            <Nullable>enable</Nullable>
                            <StartupObject>%s</StartupObject>
                            <AssemblyName>%s</AssemblyName>
                            <EnableDefaultCompileItems>false</EnableDefaultCompileItems> <!-- FIX: Disable implicit item inclusion -->
                          </PropertyGroup>
                          <ItemGroup>
                            <Compile Include="%s" />
                          </ItemGroup>
                        </Project>
                        """, csMainClassName, csMainClassName, csMainClassName + ".cs"); // Include the dynamically named file, StartupObject, and AssemblyName
                    
                    Files.writeString(tempDir.resolve(csMainClassName + ".csproj"), csprojBuild);

                    // Build Step
                    // Use the dynamically named project file
                    ProcessBuilder dotnetBuild = new ProcessBuilder("dotnet", "build", "-c", "Release", tempDir.resolve(csMainClassName + ".csproj").toString(), "-o", tempDir.resolve("build").toString());
                    dotnetBuild.directory(tempDir.toFile());
                    // FIX: Redirect stdout to null (or file) to suppress verbose build output
                    dotnetBuild.redirectOutput(ProcessBuilder.Redirect.INHERIT); 
                    Process build = dotnetBuild.start();
                    
                    // The standard output (success messages) will be suppressed here. Only stderr (errors) needs streaming.
                    // Capture and stream build stderr immediately
                    StreamGobbler buildStderrGobbler = new StreamGobbler(session, build.getErrorStream(), "output", sessionId, language);
                    new Thread(buildStderrGobbler).start();

                    boolean buildFinished = build.waitFor(30, TimeUnit.SECONDS);

                    // Stop build gobbler and wait for it to finish flushing
                    buildStderrGobbler.stopRunning();
                    Thread.sleep(75);

                    if (!buildFinished || build.exitValue() != 0) {
                        // The detailed errors have already been streamed by the gobbler
                        throw new Exception("C# build failed (Exit Code " + build.exitValue() + ")");
                    }
                    
                    // Execution Step: Execute the resulting assembly.
                    // FIX: Removed unnecessary '/net8.0/' subdirectory from the path for the final executable.
                    return new String[]{"dotnet", "exec", tempDir.resolve("build/" + csMainClassName + ".dll").toString()};
                    
                } catch (Exception e) {
                    // Fallback to mono if dotnet fails (older environment support)
                    Path csFileFallback = tempDir.resolve(csMainClassName + ".cs");
                    
                    // CRITICAL FIX: The file contents were already written earlier, only compilation is needed.
                    
                    // CRITICAL FIX: Removed the unsupported --main flag for mcs fallback.
                    ProcessBuilder mcs = new ProcessBuilder("mcs", csFileFallback.toString(), "-out:" + tempDir.resolve(csMainClassName + ".exe").toString());
                    mcs.directory(tempDir.toFile());
                    Process csCompile = mcs.start();
                    
                    // Capture and stream mono compile output immediately
                    StreamGobbler monoStdoutGobbler = new StreamGobbler(session, csCompile.getInputStream(), "output", sessionId, language);
                    StreamGobbler monoStderrGobbler = new StreamGobbler(session, csCompile.getErrorStream(), "output", sessionId, language);
                    new Thread(monoStdoutGobbler).start();
                    new Thread(monoStderrGobbler).start();

                    boolean csFinished = csCompile.waitFor(20, TimeUnit.SECONDS);

                    // Stop mono gobblers and wait for them to finish flushing
                    monoStdoutGobbler.stopRunning();
                    monoStderrGobbler.stopRunning();
                    Thread.sleep(75);

                    if (csFinished && csCompile.exitValue() == 0) {
                        // Execution of the compiled mono executable
                        return new String[]{"mono", tempDir.resolve(csMainClassName + ".exe").toString()};
                    } else {
                        // The detailed errors have already been streamed by the gobblers
                        throw new Exception("C# compilation failed (Exit Code " + csCompile.exitValue() + "): Neither dotnet nor mono execution succeeded.");
                    }
                }
                
            case "javascript":
                Path javascriptFile = tempDir.resolve("main.js");
                String jsWrapper = createEnhancedJavaScriptWrapper(code);
                Files.writeString(javascriptFile, jsWrapper);
                return new String[]{"node", javascriptFile.toString()};
                
            case "ruby":
                Path rbFile = tempDir.resolve("main.rb");
                // FIX: Prepend magic comment to ensure UTF-8 encoding
                String rubyCodeWithEncoding = "# coding: utf-8\n" + code;
                Files.writeString(rbFile, rubyCodeWithEncoding);
                // -l for line-buffering, -W0 to suppress warnings
                return new String[]{"ruby", "-W0", "-l", rbFile.toString()};
                
            default:
                return null;
        }
    }
    
    // Enhanced JavaScript wrapper
    private String createEnhancedJavaScriptWrapper(String userCode) {
        if (userCode.contains("readline.createInterface") && userCode.contains("question")) {
            return userCode;
        }
        
        return """
// Enhanced JavaScript wrapper with proper termination
const readline = require('readline');
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
});

function input(prompt) {
    return new Promise((resolve) => {
        rl.question(prompt, (answer) => {
            resolve(answer);
        });
    });
}

global.input = input;

function cleanup() {
    try {
        rl.close();
        if (process.stdin.readable) {
            process.stdin.destroy();
        }
        process.exit(0);
    } catch (error) {
        process.exit(0);
    }
}

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);
process.on('exit', cleanup);

async function main() {
    try {
""" + indentCode(userCode, "        ") + """
    } catch (error) {
        console.error('Error:', error);
    } finally {
        setTimeout(cleanup, 100);
    }
}

main().then(() => {
    setTimeout(cleanup, 50);
}).catch((error) => {
    console.error('Execution error:', error);
    setTimeout(cleanup, 50);
});

setTimeout(() => {
    console.log('Force cleanup after timeout');
    cleanup();
}, 30000);
""";
    }
    
    private String indentCode(String code, String indent) {
        return code.lines()
                    .map(line -> line.trim().isEmpty() ? "" : indent + line)
                    .collect(Collectors.joining("\n"));
    }
    
    private String getErrorOutput(InputStream errorStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
            return error.toString();
        } catch (IOException e) {
            return "Error reading compilation output";
        }
    }
    
    private void killProcess(String sessionId) {
        Process process = processes.remove(sessionId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        
        PrintWriter writer = writers.remove(sessionId);
        if (writer != null) {
            writer.close();
        }
    }
    
    private void cleanupUniversal(String sessionId, Path tempDir) {
        killProcess(sessionId);
        sessionLanguages.remove(sessionId);
        inputRequestedFlags.remove(sessionId);
        
        if (tempDir != null) {
            new Thread(() -> {
                try {
                    Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception e) {
                    System.err.println("Cleanup error: " + e.getMessage());
                }
            }).start();
        }
    }
    
    private void sendMessage(WebSocketSession session, String type, String data) {
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", type);
                message.put("data", data);
                message.put("timestamp", System.currentTimeMillis());
                
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                System.err.println("❌ Failed to send message: " + e.getMessage());
            }
        }
    }
}   
