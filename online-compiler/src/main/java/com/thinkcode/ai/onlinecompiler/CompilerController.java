package com.thinkcode.ai.onlinecompiler;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class CompilerController {

    // Removed the @Autowired ContainerPoolManager as it no longer exists

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
        // This REST endpoint is now a backup for the WebSocket handler.
        // It provides a simple status check or basic execution message.
        return ResponseEntity.ok(new CompileResponse(
            "Please use the WebSocket interface for full interactive execution.",
            "", 
            true
        ));
    }

    @GetMapping("/api/status")
    public Map<String, String> getStatus() {
        return Map.of(
            "status", "Online",
            "mode", "Local System Execution",
            "platform", "Hugging Face Spaces"
        );
    }

    private String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}