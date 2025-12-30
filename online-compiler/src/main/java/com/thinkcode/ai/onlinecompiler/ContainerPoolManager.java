package com.thinkcode.ai.onlinecompiler;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class ContainerPoolManager {

    private final Map<String, BlockingQueue<String>> containerPools = new ConcurrentHashMap<>();

    private final int PREWARM_COUNT = 0;

    @PostConstruct
    public void initializePools() throws Exception {
        List<String> supportedLanguages = List.of(
            "python", "java", "cpp", "go", "c", "csharp", "javascript", "ruby", "php"
        );

        ExecutorService executor = Executors.newFixedThreadPool(5); // Parallel start with 5 threads

        for (String lang : supportedLanguages) {
            BlockingQueue<String> pool = new LinkedBlockingQueue<>();
            containerPools.put(lang, pool);
            for (int i = 0; i < PREWARM_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        String containerId = startContainer(lang);
                        pool.offer(containerId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(2, TimeUnit.MINUTES);
        if (!finished) {
            System.err.println("Warning: Not all containers started within timeout");
        }
    }

    private String startContainer(String language) throws Exception {
    String imageName = getImageForLanguage(language);

    if (language.equalsIgnoreCase("php")) {
        // Use php-sockets image and install sockets extension if needed
        imageName = "php-sockets:8.3";
    }

    ProcessBuilder pb = new ProcessBuilder(
        "docker", "run", "-dit", "--rm", imageName, "sleep", "infinity"
    );

    Process process = pb.start();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String containerId = reader.readLine().trim();
        process.waitFor();
        System.out.println("Started container: " + containerId + " for language: " + language);
        return containerId;
    }
}


    public String leaseContainer(String language) throws InterruptedException {
        BlockingQueue<String> pool = containerPools.get(language);
        if (pool == null) throw new IllegalArgumentException("Unsupported language: " + language);
        System.out.println("Leasing container for language: " + language);
        return pool.take();
    }

    public void releaseContainer(String language, String containerId) {
        BlockingQueue<String> pool = containerPools.get(language);
        if (pool != null) {
            // Optionally: reset container state here
            pool.offer(containerId);
            System.out.println("Released container: " + containerId + " back to pool for language: " + language);
        }
    }

    private String getImageForLanguage(String language) {
        switch(language.toLowerCase()) {
            case "python": return "python:3.10";
            case "java": return "openjdk:21";
            case "cpp": return "gcc:latest";
            case "go": return "golang:1.21";
            case "c": return "gcc:latest";
            case "csharp": return "mcr.microsoft.com/dotnet/sdk:7.0";
            case "javascript": return "node:18";
            case "ruby": return "ruby:3";
            case "php": return "php-sockets:8.3";
            case "typescript": return "node:18";

        
            
            default: throw new IllegalArgumentException("No Docker image for language: " + language);
        }
    }
}
