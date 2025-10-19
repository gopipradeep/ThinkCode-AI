package com.thinkcode.ai.onlinecompiler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class CompilerController {

    @Autowired
    private ContainerPoolManager containerPoolManager;

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
        public String getError() { return error; }
        public boolean isSuccess() { return success; }
    }

    @PostMapping("/api/compile")
    public ResponseEntity<CompileResponse> compileCode(@RequestBody CompileRequest request) {
        String code = request.getCode();
        String language = request.getLanguage().toLowerCase();
        String input = request.getInput();

        try {
            String containerId = containerPoolManager.leaseContainer(language);

            try {
                Path tempDir = Files.createTempDirectory("codeexec");
                Path codeFile = null;
                String compileCmd = null;
                String runCmd = null;

                switch (language) {
                    case "java":
                        codeFile = tempDir.resolve("Main.java");
                        Files.writeString(codeFile, code);
                        compileCmd = "javac /app/Main.java";
                        runCmd = "java -cp /app Main";
                        break;
                    case "python":
                        codeFile = tempDir.resolve("main.py");
                        Files.writeString(codeFile, code);
                        runCmd = "python3 -u /app/main.py";
                        break;
                    case "cpp":
                        codeFile = tempDir.resolve("main.cpp");
                        Files.writeString(codeFile, code);
                        compileCmd = "g++ /app/main.cpp -o /app/a.out";
                        runCmd = "/app/a.out";
                        break;
                    case "go":
                        codeFile = tempDir.resolve("main.go");
                        Files.writeString(codeFile, code);
                        runCmd = "go run /app/main.go";
                        break;
                    case "c":
                        codeFile = tempDir.resolve("main.c");
                        Files.writeString(codeFile, code);
                        compileCmd = "clang /app/main.c -o /app/a.out";
                        runCmd = "/app/a.out";
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
                        runCmd = "dotnet run --project /app/" + projectDirName;
                        break;
                    case "javascript":
                        codeFile = tempDir.resolve("main.js");
                        Files.writeString(codeFile, code);
                        runCmd = "node /app/main.js";
                        break;
                    case "ruby":
                        codeFile = tempDir.resolve("main.rb");
                        Files.writeString(codeFile, code);
                        runCmd = "ruby /app/main.rb";
                        break;
                    case "php":
                        codeFile = tempDir.resolve("main.php");
                        Files.writeString(codeFile, code);
                        runCmd = "php -n /app/main.php";
                        break;
                    
                    default:
                        return ResponseEntity.badRequest().body(new CompileResponse("", "Unsupported language", false));
                }

                if (codeFile != null) {
                    ProcessBuilder copyPb = new ProcessBuilder("docker", "cp", codeFile.toString(), containerId + ":/app/");
                    Process copyProcess = copyPb.start();
                    copyProcess.waitFor();
                } else {
                    // Handle languages where no single code file is used, e.g., C#
                    // For simplicity, you might want to implement folder copy or other mechanism
                }

                if (compileCmd != null) {
                    ProcessBuilder compilePb = new ProcessBuilder("docker", "exec", containerId, "bash", "-c", compileCmd);
                    Process compileProcess = compilePb.start();
                    compileProcess.waitFor(10, TimeUnit.SECONDS);
                    if (compileProcess.exitValue() != 0) {
                        String err = readStream(compileProcess.getErrorStream());
                        return ResponseEntity.ok(new CompileResponse("", "Compilation error:\n" + err, false));
                    }
                }

                ProcessBuilder runPb = new ProcessBuilder("docker", "exec", containerId, "bash", "-c", runCmd);
                Process runProcess = runPb.start();

                ExecutorService executor = Executors.newFixedThreadPool(2);

                Future<String> stdoutFuture = executor.submit(() -> readStream(runProcess.getInputStream()));
                Future<String> stderrFuture = executor.submit(() -> readStream(runProcess.getErrorStream()));

                if (input != null && !input.isEmpty()) {
                    try {
                        if (runProcess.isAlive()) {
                            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(runProcess.getOutputStream()))) {
                                writer.write(input);
                                writer.flush();
                            }
                        } else {
                            System.err.println("Process terminated before input was written");
                        }
                    } catch (IOException e) {
                        System.err.println("Error writing input to process: " + e.getMessage());
                    }
                }

                boolean finished = runProcess.waitFor(20, TimeUnit.SECONDS);
                if (!finished) {
                    runProcess.destroyForcibly();
                    executor.shutdownNow();
                    return ResponseEntity.ok(new CompileResponse("", "Execution timed out", false));
                }

                String output = stdoutFuture.get();
                String error = stderrFuture.get();

                executor.shutdown();

                return ResponseEntity.ok(new CompileResponse(output, error, error.isEmpty()));

            } finally {
                containerPoolManager.releaseContainer(language, containerId);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new CompileResponse("", "Server error: " + e.getMessage(), false));
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
