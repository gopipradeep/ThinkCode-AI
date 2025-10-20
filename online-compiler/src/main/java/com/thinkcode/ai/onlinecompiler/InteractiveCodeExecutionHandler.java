package com.thinkcode.ai.onlinecompiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    // --- Process Management Maps ---
    // Key is ALWAYS the unique WebSocket Session ID for true sandboxing
    private static final Map<String, Process> processes = new ConcurrentHashMap<>();
    private static final Map<String, PrintWriter> writers = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionLanguages = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> inputRequestedFlags = new ConcurrentHashMap<>();

    // --- Execution Locking ---
    // Key is ALWAYS the unique WebSocket Session ID
    private static final Map<String, AtomicBoolean> executionLocks = new ConcurrentHashMap<>();

    // --- Collaboration Session Maps (for code sync and chat ONLY) ---
    private static final Map<String, Map<String, String>> collaborationSessions = new ConcurrentHashMap<>();
    private static final Map<String, String> wsSessionToCollabId = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, WebSocketSession>> collabIdToSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- StreamGobbler (Handles output ONLY for the originating session) ---
    private class StreamGobbler implements Runnable {
        private final WebSocketSession session; // The originating session
        private final InputStream inputStream;
        private final String sessionId; // The key for this process is always the session ID
        private final String language;
        private volatile boolean running = true;
        private long lastOutputTime = System.currentTimeMillis();

        public StreamGobbler(WebSocketSession session, InputStream inputStream, String language) {
            this.session = session;
            this.inputStream = inputStream;
            this.sessionId = session.getId();
            this.language = language;
        }

        public void stopRunning() { this.running = false; }

        @Override
        public void run() {
            // Using UTF_8 encoding for reading process output
            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                char[] buffer = new char[512];
                int bytesRead;
                while (running) {
                    if (reader.ready()) {
                        bytesRead = reader.read(buffer);
                        if (bytesRead > 0) {
                            String output = new String(buffer, 0, bytesRead);
                            // ALWAYS send output ONLY to the originating session
                            sendMessage(this.session, "output", output);
                            lastOutputTime = System.currentTimeMillis();
                            inputRequestedFlags.computeIfPresent(this.sessionId, (k, v) -> { v.set(false); return v; });
                        } else if (bytesRead == -1) { // End of stream
                            break;
                        }
                    } else {
                        // Check if input might be needed
                        Process process = processes.get(this.sessionId);
                        AtomicBoolean inputRequested = inputRequestedFlags.get(this.sessionId);
                        if (process != null && process.isAlive() && inputRequested != null && !inputRequested.get()) {
                            long timeSinceLastOutput = System.currentTimeMillis() - lastOutputTime;
                            if (timeSinceLastOutput > getTimeoutForLanguage(language)) {
                                sendMessage(this.session, "input_request", "");
                                inputRequested.set(true);
                                System.out.println("⏳ Input requested for " + language + " on " + this.sessionId);
                            }
                        }
                        Thread.sleep(5); // Prevent busy-waiting
                    }
                }
                // Drain any remaining output after stopRunning() is called or stream ends
                while (reader.ready()) {
                    bytesRead = reader.read(buffer);
                    if (bytesRead > 0) {
                        String finalOutput = new String(buffer, 0, bytesRead);
                        sendMessage(this.session, "output", finalOutput);
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                // Silently ignore IOExceptions, often happens when process is killed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            } catch (Exception e) {
                 System.err.println("❌ StreamGobbler error for " + sessionId + ": " + e.getMessage());
            } finally {
                try { if (inputStream != null) inputStream.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private int getTimeoutForLanguage(String language) {
        switch (language) {
            case "c": case "go": case "ruby": return 300;
            case "javascript": case "python": return 350;
            case "java": return 500;
            default: return 450;
        }
    }

    // --- WebSocket Lifecycle Methods ---

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("✅ WebSocket Connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        String sessionId = session.getId(); // The unique key for this user
        String collabId = wsSessionToCollabId.get(sessionId); // Check if they are in a collab room

        switch (type) {
            case "execute":
                // Lock is now PER-SESSION, enabling concurrent execution in collab rooms
                AtomicBoolean isLocked = executionLocks.computeIfAbsent(sessionId, k -> new AtomicBoolean(false));

                if (isLocked.compareAndSet(false, true)) { // Attempt to acquire the lock
                    String language = node.get("language").asText();
                    String code = node.get("code").asText();
                    // Kill any previous process *for this specific user*
                    killProcess(sessionId);
                    // Start execution in a new thread, associated only with this session
                    new Thread(() -> executeUniversal(session, language, code)).start();
                } else {
                    // Lock was already held by this user
                    sendMessage(session, "error", "You already have an execution in progress.");
                }
                break;

            case "input":
                String inputData = node.get("data").asText();
                // Send input to the process associated with this specific session
                handleInputUniversal(sessionId, inputData, session);
                break;

            case "stop":
                // Kill the process associated with this specific session
                killProcess(sessionId);
                // Send completion message ONLY to this user
                sendMessage(session, "execution_complete", "Execution stopped");
                // Release the lock for this specific session
                executionLocks.computeIfPresent(sessionId, (k, v) -> {
                    v.set(false);
                    return v;
                });
                break;

            // --- Collaboration Handlers (Unaffected by execution changes) ---
            case "create_collab_session":
                handleCreateCollabSession(session, node);
                break;
            case "join_collab_session":
                handleJoinCollabSession(session, node);
                break;
            case "sync_code":
                // Sync code only makes sense if the user is in a collab session
                if (collabId != null) handleSyncCode(session, node, collabId);
                break;
            case "chat_message":
                 // Chat only makes sense if the user is in a collab session
                if (collabId != null) handleChatMessage(session, node, collabId);
                break;

            case "ping":
                sendMessage(session, "pong", "Server alive");
                break;

            default:
                 System.out.println("⚠️ Unknown message type received: " + type);
                 break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        System.out.println("🔌 WebSocket Disconnected: " + sessionId + " Status: " + status);

        // --- Universal Cleanup for the disconnected session ---
        killProcess(sessionId); // Kill any running process
        // Remove all state associated with this specific session
        inputRequestedFlags.remove(sessionId);
        executionLocks.remove(sessionId);
        sessionLanguages.remove(sessionId);
        writers.remove(sessionId); // Ensure writer is removed

        // --- Collab-specific cleanup ---
        String collabId = wsSessionToCollabId.remove(sessionId); // Remove user from collab mapping
        if (collabId != null) {
            collabIdToSessions.computeIfPresent(collabId, (k, sessions) -> {
                sessions.remove(sessionId); // Remove session from the room's list
                if (sessions.isEmpty()) {
                    System.out.println("🚪 Collab session is now empty: " + collabId);
                    // Optionally: Schedule collab session data for cleanup after inactivity
                    // collaborationSessions.remove(collabId); // Or keep data for re-joining? Decision needed.
                    return null; // Remove the session map entry if empty
                }
                // Notify remaining users
                broadcastToCollabSession(collabId, "collab_update", "A user has left the session.", sessionId);
                return sessions; // Return updated map
            });
        }
    }

     @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("❌ WebSocket Transport Error for " + session.getId() + ": " + exception.getMessage());
        // Consider closing the session or performing specific cleanup
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException e) {
             System.err.println("Error closing session on transport error: " + e.getMessage());
        } finally {
             // Ensure cleanup happens even if close fails
             afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
        }
    }


    // --- Collaboration Feature Methods (for sync/chat only) ---

    private void handleCreateCollabSession(WebSocketSession session, JsonNode node) {
        String collabId = node.get("sessionId").asText();
        String code = node.get("code").asText();
        String language = node.get("language").asText();
        String hostId = node.get("hostId").asText();
        String wsSessionId = session.getId();

        Map<String, String> sessionData = new ConcurrentHashMap<>(); // Use ConcurrentHashMap
        sessionData.put("code", code);
        sessionData.put("language", language);
        sessionData.put("hostId", hostId);
        collaborationSessions.put(collabId, sessionData);
        
        wsSessionToCollabId.put(wsSessionId, collabId);
        collabIdToSessions.computeIfAbsent(collabId, k -> new ConcurrentHashMap<>()).put(wsSessionId, session);
        
        System.out.println("✨ Collab session created: " + collabId + " by " + hostId);
        sendMessage(session, "collab_session_created", collabId);
    }

    private void handleJoinCollabSession(WebSocketSession session, JsonNode node) {
        String collabId = node.get("sessionId").asText();
        String wsSessionId = session.getId();
        String displayName = node.has("displayName") ? node.get("displayName").asText("User") : "User"; // Safer access
        
        Map<String, String> sessionData = collaborationSessions.get(collabId);

        if (sessionData != null) {
            wsSessionToCollabId.put(wsSessionId, collabId);
            collabIdToSessions.computeIfAbsent(collabId, k -> new ConcurrentHashMap<>()).put(wsSessionId, session);

            String jsonSessionData = "";
            try {
                jsonSessionData = objectMapper.writeValueAsString(sessionData);
            } catch (IOException e) {
                 System.err.println("❌ Error serializing session data for join: " + e.getMessage());
                 sendMessage(session, "error", "Internal error joining session.");
                 return;
            }
            
            sendMessage(session, "initial_code_sync", jsonSessionData);
            System.out.println("➡️ Session " + wsSessionId + " (" + displayName + ") joined collab: " + collabId);
            
            broadcastToCollabSession(collabId, "collab_update", displayName + " has joined the session.", wsSessionId);
        } else {
            sendMessage(session, "error", "Collaboration session not found or expired.");
            // Don't map if session doesn't exist
        }
    }
    
    // Sync code affects the *shared* data and notifies others
    private void handleSyncCode(WebSocketSession session, JsonNode node, String collabId) {
        String senderSessionId = session.getId();
        String newCode = node.get("code").asText();
        String newLanguage = node.get("language").asText();

        Map<String, String> updatedData = collaborationSessions.computeIfPresent(collabId, (k, data) -> {
            data.put("code", newCode);
            data.put("language", newLanguage);
            return data;
        });
        
        if (updatedData == null) {
            sendMessage(session, "error", "Collaboration session lost during sync.");
            return;
        }

        Map<String, String> broadcastData = new HashMap<>();
        broadcastData.put("code", newCode);
        broadcastData.put("language", newLanguage);
        
        String jsonBroadcastData = "";
        try {
            jsonBroadcastData = objectMapper.writeValueAsString(broadcastData);
        } catch (IOException e) {
             System.err.println("❌ Error serializing sync data: " + e.getMessage());
             sendMessage(session, "error", "Internal error syncing code.");
             return;
        }
        // Broadcast code change to all *other* participants
        broadcastToCollabSession(collabId, "code_sync", jsonBroadcastData, senderSessionId);
    }

    // Chat messages are broadcast to everyone in the room
    private void handleChatMessage(WebSocketSession session, JsonNode node, String collabId) {
        JsonNode chatDataNode = node.get("data");
        if (chatDataNode == null) return; 

        String jsonChatData = chatDataNode.toString();
        // Broadcast chat to ALL participants (including sender)
        broadcastToCollabSession(collabId, "chat_message", jsonChatData, null);
    }
    
    // --- SANDBOXED EXECUTION METHOD ---
    private void executeUniversal(WebSocketSession session, String language, String code) {
        String sessionId = session.getId(); // Key for sandbox is always the session ID
        Path tempDir = null;
        Process process = null;
        StreamGobbler stdoutGobbler = null;
        StreamGobbler stderrGobbler = null;
        
        try {
             System.out.println("🚀 Starting execution for session: " + sessionId + " - Language: " + language);
            
            sessionLanguages.put(sessionId, language);
            inputRequestedFlags.put(sessionId, new AtomicBoolean(false));
            sendMessage(session, "execution_started", ""); // Send ONLY to originator
            
            tempDir = Files.createTempDirectory("exec_" + sessionId + "_");
            String[] command = getCommand(language, code, tempDir); // Fetch command array
            
            if (command == null) {
                sendMessage(session, "error", "Unsupported language: " + language);
                // No need to release lock here, finally block handles it
                return;
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            
            // Set up environment variables
            Map<String, String> env = pb.environment();
            env.put("PYTHONUNBUFFERED", "1");
            env.put("NODE_NO_READLINE", "1");
            env.put("TERM", "dumb"); // Use a basic terminal type
            env.put("LC_ALL", "en_US.UTF-8"); // Ensure UTF-8 locale
            env.put("LANG", "en_US.UTF-8");
            if ("javascript".equals(language)) {
                env.put("NODE_NO_WARNINGS", "1");
                env.put("NODE_OPTIONS", "--no-deprecation");
                env.put("NODE_DISABLE_COLORS", "1");
            }
            if ("php".equals(language)) {
                env.put("XDEBUG_MODE", "off"); // Disable Xdebug if present
            }
            if ("ruby".equals(language)) {
                env.put("RUBYOPT", "-EUTF-8:UTF-8"); // Force UTF-8 encoding for Ruby
            }
            
            process = pb.start();
            processes.put(sessionId, process); // Store the process
            
            // Create writer with UTF-8 encoding and auto-flush
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
            writers.put(sessionId, writer); // Store the writer
            
            // Start gobblers for stdout and stderr
            stdoutGobbler = new StreamGobbler(session, process.getInputStream(), language);
            stderrGobbler = new StreamGobbler(session, process.getErrorStream(), language);
            new Thread(stdoutGobbler).start();
            new Thread(stderrGobbler).start();
            
            // Wait for process completion with timeout
            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5-minute timeout
            
            // Signal gobblers to stop *after* process ends or times out
            if (stdoutGobbler != null) stdoutGobbler.stopRunning();
            if (stderrGobbler != null) stderrGobbler.stopRunning();

            // Brief pause to allow gobblers to finish reading remaining output
            Thread.sleep(75);
            
            if (finished) {
                int exitCode = process.exitValue();
                System.out.println("✅ Process for " + sessionId + " completed - Exit code: " + exitCode);
                sendMessage(session, "execution_complete", "Exit code: " + exitCode);
            } else { // Timeout occurred
                 System.out.println("⏰ Process for " + sessionId + " timeout - Forcibly destroying.");
                 killProcess(sessionId); // Ensure it's killed forcefully
                 sendMessage(session, "error", "Execution timed out after 5 minutes.");
                 sendMessage(session, "execution_complete", "Exit code: 124 (Timeout)");
            }
            
        } catch (IOException e) {
             System.err.println("❌ IOException during execution for " + sessionId + ": " + e.getMessage());
             sendMessage(session, "error", "Execution failed (IO): " + e.getMessage());
             sendMessage(session, "execution_complete", "Exit code: 1");
        } catch (InterruptedException e) {
             System.err.println("❌ Execution interrupted for " + sessionId + ": " + e.getMessage());
             Thread.currentThread().interrupt(); // Restore interrupted status
             sendMessage(session, "error", "Execution was interrupted.");
             sendMessage(session, "execution_complete", "Exit code: 130 (Interrupted)");
        } catch (Exception e) { // Catch broader exceptions like compilation errors
             System.err.println("❌ General execution error for " + sessionId + ": " + e.getMessage());
             sendMessage(session, "error", "Execution failed: " + e.getMessage());
             sendMessage(session, "execution_complete", "Exit code: 1");
        } finally {
            // --- CRITICAL: Always release the lock for this specific session ---
            executionLocks.computeIfPresent(sessionId, (k, v) -> {
                v.set(false); // Set lock back to false
                System.out.println("🔓 Lock released for: " + sessionId);
                return v; // Return the updated AtomicBoolean
            });
            
            // Clean up temporary files and ensure process is killed
            cleanupUniversal(sessionId, tempDir);
        }
    }

    private void handleInputUniversal(String sessionId, String inputData, WebSocketSession session) {
        PrintWriter writer = writers.get(sessionId);
        if (writer != null) {
            try {
                // Check if the process is still alive before writing
                Process process = processes.get(sessionId);
                if (process != null && process.isAlive()) {
                    writer.println(inputData);
                    writer.flush(); // Ensure data is sent immediately
                    // No need to sleep here, StreamGobbler handles input_request logic
                    inputRequestedFlags.computeIfPresent(sessionId, (k, v) -> { v.set(false); return v; });
                } else {
                     sendMessage(session, "error", "Cannot send input: Process is not running.");
                }
            } catch (Exception e) { // Catch potential errors during write/flush
                 System.err.println("❌ Failed to send input for " + sessionId + ": " + e.getMessage());
                 sendMessage(session, "error", "Failed to send input: " + e.getMessage());
            }
        } else {
             sendMessage(session, "error", "Cannot send input: No active process found for your session.");
        }
    }
    
    // --- FULL getCommand METHOD ---
    // (Ensure this includes all 11 language cases correctly)
    private String[] getCommand(String language, String code, Path tempDir) throws Exception {
        Process compile = null; 
        
        switch (language) {
            case "python":
                Path pyFile = tempDir.resolve("main.py");
                Files.writeString(pyFile, code, StandardCharsets.UTF_8);
                return new String[]{"python3", "-u", pyFile.toString()};
                
            case "java":
                String packageName = null; String mainClassName = "Main";
                String[] javaLines = code.split("\n");
                for (String line : javaLines) {
                    if (line.trim().startsWith("package ")) {
                        String packageLine = line.trim(); int semiIndex = packageLine.indexOf(';');
                        if (semiIndex != -1) { packageName = packageLine.substring(8, semiIndex).trim(); break; }
                    }
                }
                String javaClassNameRegex = "(?:public\\s+)?class\\s+(\\w+)"; 
                java.util.regex.Pattern javaPattern = java.util.regex.Pattern.compile(javaClassNameRegex);
                for (String line : javaLines) {
                    java.util.regex.Matcher matcher = javaPattern.matcher(line);
                    if (matcher.find()) {
                        String detectedClassName = matcher.group(1);
                        if (code.contains("public static void main")) { mainClassName = detectedClassName; break; }
                    }
                }
                Path javaFile = tempDir.resolve(mainClassName + ".java");
                Files.writeString(javaFile, code, StandardCharsets.UTF_8);
                if (packageName != null && !packageName.isEmpty()) { mainClassName = packageName + "." + mainClassName; }
                ProcessBuilder javac = new ProcessBuilder("javac", "-encoding", "UTF-8", "-d", ".", javaFile.getFileName().toString());
                javac.directory(tempDir.toFile()); compile = javac.start();
                if (!compile.waitFor(15, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                    throw new Exception("Java compilation failed:\n" + getErrorOutput(compile.getErrorStream()));
                }
                return new String[]{"java", "-cp", tempDir.toString(), mainClassName};
                
            case "cpp":
                Path cppFile = tempDir.resolve("main.cpp"); Files.writeString(cppFile, code, StandardCharsets.UTF_8);
                ProcessBuilder gpp = new ProcessBuilder("g++", "-std=c++17", "-O2", cppFile.toString(), "-o", tempDir.resolve("main").toString());
                gpp.directory(tempDir.toFile()); compile = gpp.start();
                if (!compile.waitFor(15, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                    throw new Exception("C++ compilation failed:\n" + getErrorOutput(compile.getErrorStream())); 
                }
                return new String[]{tempDir.resolve("main").toString()};
                
            
                
            case "php":
                Path phpFile = tempDir.resolve("main.php"); Files.writeString(phpFile, code, StandardCharsets.UTF_8);
                return new String[]{"php", "-n", "-d", "display_errors=1", "-d", "error_reporting=E_ALL", phpFile.toString()};
                
            
                
            case "go":
                Path goFile = tempDir.resolve("main.go"); Files.writeString(goFile, code, StandardCharsets.UTF_8);
                // Use 'go build' for potentially better error reporting and consistency
                ProcessBuilder goBuild = new ProcessBuilder("go", "build", "-o", tempDir.resolve("main").toString(), goFile.toString());
                goBuild.directory(tempDir.toFile()); compile = goBuild.start();
                 if (!compile.waitFor(20, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                    throw new Exception("Go build failed:\n" + getErrorOutput(compile.getErrorStream()));
                }
                return new String[]{tempDir.resolve("main").toString()};
                
            case "c":
                Path cFile = tempDir.resolve("main.c"); Files.writeString(cFile, code, StandardCharsets.UTF_8);
                ProcessBuilder gcc = new ProcessBuilder("gcc", "-std=c11", "-O2", cFile.toString(), "-o", tempDir.resolve("main").toString(), "-lm"); // Link math library
                gcc.directory(tempDir.toFile()); compile = gcc.start();
                if (!compile.waitFor(15, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                    throw new Exception("C compilation failed:\n" + getErrorOutput(compile.getErrorStream()));
                }
                return new String[]{tempDir.resolve("main").toString()};
                
            case "csharp":
                String csMainClassName = "Program";
                String csClassNameRegex = "(?:public\\s+)?(?:static\\s+)?class\\s+(\\w+)"; // Adjusted regex
                java.util.regex.Pattern csPattern = java.util.regex.Pattern.compile(csClassNameRegex);
                for (String line : code.split("\n")) {
                    java.util.regex.Matcher matcher = csPattern.matcher(line);
                    if (matcher.find()) { csMainClassName = matcher.group(1); break; }
                }
                Path csFile = tempDir.resolve(csMainClassName + ".cs");
                Files.writeString(csFile, code, StandardCharsets.UTF_8);
                try { // Try .NET Core build first
                    String csprojContent = String.format("""
                        <Project Sdk="Microsoft.NET.Sdk">
                          <PropertyGroup>
                            <OutputType>Exe</OutputType>
                            <TargetFramework>net8.0</TargetFramework>
                            <ImplicitUsings>enable</ImplicitUsings>
                            <Nullable>enable</Nullable>
                            <StartupObject>%s</StartupObject>
                            <AssemblyName>%s</AssemblyName>
                            <EnableDefaultCompileItems>false</EnableDefaultCompileItems>
                            <WarningLevel>4</WarningLevel> </PropertyGroup>
                          <ItemGroup>
                            <Compile Include="%s" />
                          </ItemGroup>
                        </Project>
                        """, csMainClassName, csMainClassName, csFile.getFileName().toString());
                    Files.writeString(tempDir.resolve(csMainClassName + ".csproj"), csprojContent, StandardCharsets.UTF_8);
                    ProcessBuilder dotnetBuild = new ProcessBuilder("dotnet", "build", "-c", "Release", tempDir.resolve(csMainClassName + ".csproj").toString(), "-o", tempDir.resolve("build").toString());
                    dotnetBuild.directory(tempDir.toFile()); compile = dotnetBuild.start();
                    if (!compile.waitFor(30, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                        throw new Exception("C# build failed (dotnet):\n" + getErrorOutput(compile.getErrorStream()));
                    }
                    return new String[]{"dotnet", "exec", tempDir.resolve("build/" + csMainClassName + ".dll").toString()};
                } catch (Exception eDotNet) { // Fallback to mono
                    System.out.println("⚠️ .NET build failed, attempting mono fallback: " + eDotNet.getMessage());
                    ProcessBuilder mcs = new ProcessBuilder("mcs", "-out:" + tempDir.resolve(csMainClassName + ".exe").toString(), csFile.toString());
                    mcs.directory(tempDir.toFile()); compile = mcs.start();
                    if (!compile.waitFor(20, TimeUnit.SECONDS) || compile.exitValue() != 0) {
                        throw new Exception("C# compilation failed (mono fallback):\n" + getErrorOutput(compile.getErrorStream()) + "\n.NET error was:\n" + eDotNet.getMessage());
                    }
                    return new String[]{"mono", tempDir.resolve(csMainClassName + ".exe").toString()};
                }
                
            case "javascript":
                Path jsFile = tempDir.resolve("main.js");
                String jsWrapper = createEnhancedJavaScriptWrapper(code);
                Files.writeString(jsFile, jsWrapper, StandardCharsets.UTF_8);
                return new String[]{"node", jsFile.toString()};
                
            case "ruby":
                Path rbFile = tempDir.resolve("main.rb");
                String rubyCodeWithEncoding = "# coding: utf-8\n" + code;
                Files.writeString(rbFile, rubyCodeWithEncoding, StandardCharsets.UTF_8);
                return new String[]{"ruby", "-W0", rbFile.toString()}; // Removed -l flag, might interfere
                
            default:
                return null; // Unsupported language
        }
    }

    // --- Other Helper Methods (mostly unchanged, ensure UTF-8 is used) ---

    private void killProcess(String sessionId) {
        Process process = processes.remove(sessionId);
        if (process != null) {
            if (process.isAlive()) {
                 System.out.println("🛑 Forcibly destroying process for session: " + sessionId);
                 process.destroyForcibly();
                 try {
                     // Brief wait to allow OS cleanup
                     process.waitFor(50, TimeUnit.MILLISECONDS);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                 }
            } else {
                 System.out.println("ⓘ Process for session " + sessionId + " already terminated.");
            }
        }
        // Close and remove the writer associated with the session
        PrintWriter writer = writers.remove(sessionId);
        if (writer != null) {
            writer.close();
        }
    }
    
    // Cleanup ensures process is killed and temp dir is deleted
    private void cleanupUniversal(String sessionId, Path tempDir) {
        killProcess(sessionId); // Ensure process and writer are handled
        sessionLanguages.remove(sessionId);
        inputRequestedFlags.remove(sessionId);
        // Note: Lock is released in executeUniversal's finally block, not here.
        
        if (tempDir != null && Files.exists(tempDir)) {
             System.out.println("🧹 Cleaning up temp directory: " + tempDir);
             // Run cleanup in a separate thread to avoid blocking
             new Thread(() -> {
                try {
                    // Walk the directory tree in reverse order and delete files/dirs
                    Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                 System.err.println("⚠️ Failed to delete file during cleanup: " + file.getAbsolutePath());
                            }
                        });
                } catch (IOException e) {
                     System.err.println("❌ Error during temp directory cleanup for " + sessionId + ": " + e.getMessage());
                }
            }).start();
        }
    }

    // Broadcasts to all except excludedSessionId
    private void broadcastToCollabSession(String collabId, String type, String data, String excludedSessionId) {
        Map<String, WebSocketSession> sessions = collabIdToSessions.get(collabId);
        if (sessions != null) {
            sessions.forEach((sessionId, session) -> {
                if (excludedSessionId == null || !sessionId.equals(excludedSessionId)) {
                    sendMessage(session, type, data);
                }
            });
        }
    }
    
    // Sends a message to a single session
    private void sendMessage(WebSocketSession session, String type, String data) {
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", type);
                // Smartly handle data based on type (JSON or raw string)
                if ("initial_code_sync".equals(type) || "code_sync".equals(type) || "chat_message".equals(type)) {
                    try {
                        message.put("data", objectMapper.readTree(data));
                    } catch (Exception e) { // Fallback if data isn't valid JSON for these types
                         System.err.println("⚠️ Expected JSON data for type '" + type + "' but got raw string. Sending as raw.");
                         message.put("data", data);
                    }
                } else {
                    message.put("data", data); // For output, error, etc.
                }
                message.put("timestamp", System.currentTimeMillis());
                
                String jsonMessage = objectMapper.writeValueAsString(message);
                // Use synchronized block for thread safety when sending
                synchronized(session) {
                    if (session.isOpen()) { // Double-check isOpen inside synchronized block
                        session.sendMessage(new TextMessage(jsonMessage));
                    }
                }
            } catch (IOException e) {
                 System.err.println("❌ Failed to send WebSocket message to " + session.getId() + ": " + e.getMessage());
                 // Consider closing the session if sending fails repeatedly
            } catch (Exception e) {
                // Catch potential JSON processing errors
                 System.err.println("❌ Error preparing WebSocket message for " + session.getId() + ": " + e.getMessage());
            }
        } else {
             System.out.println("⚠️ Attempted to send message to closed or null session.");
        }
    }

    // JavaScript wrapper (unchanged, ensure it's correct)
    private String createEnhancedJavaScriptWrapper(String userCode) {
        if (userCode.contains("readline.createInterface") && userCode.contains("question")) { return userCode; }
        return """
// Enhanced JavaScript wrapper with proper termination & async support
const readline = require('readline');
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
let isRlClosed = false; // Flag to prevent multiple closes

function input(prompt) {
    if (isRlClosed) return Promise.resolve(''); // Handle closed interface
    return new Promise((resolve) => {
        rl.question(prompt, (answer) => { resolve(answer); });
    });
}
global.input = input; // Make input globally available

function cleanup(exitCode = 0) {
    if (!isRlClosed) {
        try { rl.close(); } catch {}
        isRlClosed = true;
    }
    // Ensure stdin is destroyed only if it's readable, preventing errors on double cleanup
    if (process.stdin.readable && !process.stdin.destroyed) {
        try { process.stdin.destroy(); } catch {}
    }
    process.exit(exitCode);
}

// Graceful exit handlers
process.on('SIGINT', () => cleanup(130)); // Ctrl+C
process.on('SIGTERM', () => cleanup(143)); // Kill signal
// process.on('exit', (code) => { console.log(`Node process exited with code ${code}`); }); // Optional: Log exit code

// Main execution wrapper with async support
(async function main() {
    let exitCode = 0;
    try {
""" + indentCode(userCode, "        ") + """
    } catch (error) {
        console.error('Execution Error:', error);
        exitCode = 1; // Indicate an error occurred
    } finally {
        // Ensure cleanup happens after a short delay, allowing async operations to potentially finish
        setTimeout(() => cleanup(exitCode), 150);
    }
})();

// Safety net timeout to force exit if the script hangs
const timeoutMillis = 300000; // 5 minutes (matches waitFor timeout)
const forceExitTimeout = setTimeout(() => {
    console.error(`Execution timed out after ${timeoutMillis / 1000} seconds. Forcing exit.`);
    cleanup(124); // Standard timeout exit code
}, timeoutMillis);

// Clear the timeout if the script finishes normally
process.on('exit', () => clearTimeout(forceExitTimeout));
""";
    }
    
    // Indents code lines (unchanged)
    private String indentCode(String code, String indent) {
        return code.lines()
                .map(line -> line.trim().isEmpty() ? "" : indent + line)
                .collect(Collectors.joining("\n"));
    }
    
    // Reads error stream (unchanged, uses UTF-8)
    private String getErrorOutput(InputStream errorStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
            // Read all lines, limit output size if necessary to prevent memory issues
            return reader.lines().limit(100).collect(Collectors.joining("\n"));
        } catch (IOException e) {
             System.err.println("❌ IOException reading error stream: " + e.getMessage());
             return "Error reading compilation/runtime output stream.";
        }
    }

} // End of class InteractiveCodeExecutionHandler