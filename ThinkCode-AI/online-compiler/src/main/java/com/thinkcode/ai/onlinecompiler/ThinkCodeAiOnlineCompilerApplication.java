package com.thinkcode.ai.onlinecompiler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableWebSocket  // Add this annotation here as well
@ComponentScan(basePackages = "com.thinkcode.ai.onlinecompiler")  // Explicit component scanning
public class ThinkCodeAiOnlineCompilerApplication {
    
    public static void main(String[] args) {
        System.out.println("🚀 Starting ThinkCode AI Online Compiler...");
        SpringApplication.run(ThinkCodeAiOnlineCompilerApplication.class, args);
        System.out.println("✅ Application startup completed");
    }
}
