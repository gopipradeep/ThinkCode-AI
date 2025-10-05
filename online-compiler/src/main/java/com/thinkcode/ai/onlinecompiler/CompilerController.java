package com.thinkcode.ai.onlinecompiler;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.HashMap;

@RestController
@CrossOrigin(origins = "*")
public class CompilerController {

    public static class CompileRequest {
        private String code;
        private String language;
        private String input;
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
    }

    public static class CompileResponse {
        private String output;
        private String error;
        private boolean success;
        
        public CompileResponse(String output, String error, boolean success) {
            this.output = output;
            this.error = error;
            this.success = success;
        }
        
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    @PostMapping("/api/compile")
    public ResponseEntity<CompileResponse> compileCode(@RequestBody CompileRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        String input = request.getInput();
        String result = "";
        String errorMsg = "";
        boolean success = true;
        String tempFileName = null;
        String[] compileCommand = null;
        String[] runCommand = null;

        try {
            Path tempDir = Files.createTempDirectory("compiler_temp_");

            switch (language) {
                case "java":
                    tempFileName = "Main.java";
                    compileCommand = new String[]{"javac", tempDir.resolve(tempFileName).toString()};
                    runCommand = new String[]{"java", "-cp", tempDir.toString(), "Main"};
                    break;
                case "python":
                    tempFileName = "main.py";
                    runCommand = new String[]{"python3", tempDir.resolve(tempFileName).toString()};
                    break;
                case "cpp":
                    tempFileName = "main.cpp";
                    compileCommand = new String[]{"g++", tempDir.resolve(tempFileName).toString(), "-o", tempDir.resolve("a.out").toString()};
                    runCommand = new String[]{tempDir.resolve("a.out").toString()};
                    break;
                case "go":
                    tempFileName = "main.go";
                    runCommand = new String[]{"go", "run", tempDir.resolve(tempFileName).toString()};
                    break;
                case "c":
                    tempFileName = "main.c";
                    compileCommand = new String[]{"clang", tempDir.resolve(tempFileName).toString(), "-o", tempDir.resolve("a.out").toString()};
                    runCommand = new String[]{tempDir.resolve("a.out").toString()};
                    break;
                case "csharp":
                    String projectDirName = "csharp-project";
                    Path projectDir = tempDir.resolve(projectDirName);
                    Files.createDirectories(projectDir);
                    ProcessBuilder newProjectPb = new ProcessBuilder("dotnet", "new", "console", "--no-restore");
                    newProjectPb.directory(projectDir.toFile());
                    Process newProjectProcess = newProjectPb.start();
                    newProjectProcess.waitFor(10, TimeUnit.SECONDS);
                    Path programFile = projectDir.resolve("Program.cs");
                    Files.writeString(programFile, code);
                    runCommand = new String[]{"dotnet", "run", "--project", projectDir.toString()};
                    break;
                case "javascript":
                    tempFileName = "main.js";
                    runCommand = new String[]{"node", tempDir.resolve(tempFileName).toString()};
                    break;
                case "ruby":
                    tempFileName = "main.rb";
                    runCommand = new String[]{"ruby", tempDir.resolve(tempFileName).toString()};
                    break;
                case "rust":
                    tempFileName = "main.rs";
                    String executableName = "output_file";
                    compileCommand = new String[]{"rustc", tempDir.resolve(tempFileName).toString(), "-o", tempDir.resolve(executableName).toString()};
                    runCommand = new String[]{tempDir.resolve(executableName).toString()};
                    break;
                case "swift":
                    tempFileName = "main.swift";
                    compileCommand = new String[]{"swiftc", tempDir.resolve(tempFileName).toString()};
                    runCommand = new String[]{tempDir.resolve(tempFileName.replace(".swift", "")).toString()};
                    break;
                case "kotlin":
                    tempFileName = "Main.kt";
                    compileCommand = new String[]{"kotlinc", tempDir.resolve(tempFileName).toString(), "-include-runtime", "-d", tempDir.resolve("Main.jar").toString()};
                    runCommand = new String[]{"java", "-jar", tempDir.resolve("Main.jar").toString()};
                    break;
                case "php":
                    tempFileName = "main.php";
                    runCommand = new String[]{"php", "-n", tempDir.resolve(tempFileName).toString()};
                    break;
                case "typescript":
                    tempFileName = "main.ts";
                    compileCommand = new String[]{"tsc", tempDir.resolve(tempFileName).toString()};
                    runCommand = new String[]{"node", tempDir.resolve(tempFileName.replace(".ts", ".js")).toString()};
                    break;
                default:
                    return ResponseEntity.badRequest().body(
                        new CompileResponse("", "Unsupported language: " + language, false)
                    );
            }

            if (!"csharp".equals(language)) {
                Path tempFile = tempDir.resolve(tempFileName);
                Files.writeString(tempFile, code);
            }

            // Compilation step
            if (compileCommand != null) {
                ProcessBuilder compilePb = new ProcessBuilder(compileCommand);
                compilePb.directory(tempDir.toFile());
                Process compileProcess = compilePb.start();
                compileProcess.waitFor(10, TimeUnit.SECONDS);
                if (compileProcess.exitValue() != 0) {
                    String compilationError = getProcessOutput(compileProcess, "Compilation error:\n");
                    return ResponseEntity.ok(
                        new CompileResponse("", compilationError, false)
                    );
                }
            }

            // Execution step
            ProcessBuilder runPb = new ProcessBuilder(runCommand);
            runPb.directory(tempDir.toFile());
            Process runProcess = runPb.start();
            
            // Handle dynamic input
            if (input != null && !input.isEmpty()) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(runProcess.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                }
            }

            CompletableFuture<String> outputReader = CompletableFuture.supplyAsync(() -> {
                try {
                    return readInputStream(runProcess.getInputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, Executors.newSingleThreadExecutor());

            CompletableFuture<String> errorReader = CompletableFuture.supplyAsync(() -> {
                try {
                    return readInputStream(runProcess.getErrorStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, Executors.newSingleThreadExecutor());

            boolean finished = runProcess.waitFor(10, TimeUnit.SECONDS);
            
            if (!finished) {
                runProcess.destroyForcibly();
                return ResponseEntity.ok(
                    new CompileResponse("", "Execution timeout (10 seconds)", false)
                );
            }

            String standardOutput = outputReader.join();
            String errorOutput = errorReader.join();
            
            result = standardOutput;
            if (!errorOutput.isEmpty()) {
                errorMsg = errorOutput;
                success = false;
            }

            // Clean up temp directory
            Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.ok(
                new CompileResponse("", "Execution failed: " + e.getMessage(), false)
            );
        }
        
        return ResponseEntity.ok(new CompileResponse(result, errorMsg, success));
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    private String getProcessOutput(Process process, String prefix) throws IOException {
        StringBuilder output = new StringBuilder(prefix);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                output.append("ERROR: ").append(line).append("\n");
            }
        }
        return output.toString();
    }
}