package com.thinkcode.ai.onlinecompiler;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController

@RequestMapping("/gemini")
@CrossOrigin(origins = "*")
public class GeminiController {

    // Injects the API key from application.properties/environment
    @Value("${gemini.api.key}")
    private String GEMINI_API_KEY;

    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=";

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Gemini Controller is working!");
    }

    @PostMapping("/{type}")
    public ResponseEntity<Map<String, String>> handleGemini(
            @PathVariable String type,
            @RequestBody Map<String, String> payload) {

        System.out.println("✅ Request type: " + type);

        try {
            String code = payload.get("code");
            String language = payload.getOrDefault("language", "unknown");
            String executionContext = payload.getOrDefault("executionContext", ""); 

            if (code == null || code.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("result", "Error: No code provided"));
            }

            String prompt;
            String codeBlock = "\n```" + language + "\n" + code + "\n```";

            if ("analysis".equalsIgnoreCase(type)) {
                    // MODIFIED PROMPT: Now explicitly requests Big O notation immediately following the heading
                prompt = 
                        "As an expert software engineer and algorithm analyst, perform a precise and well-structured complexity analysis of the following " + language + " code. " +
                        "Your output must be a **single, clean, and compact block of HTML** — ready to be injected directly into a web page container (e.g., a <div>). " +

                            "Follow these exact formatting rules:\n" +
                            "1. Use <h4> headings for each main section.\n" +
                            "2. For 'Time Complexity' and 'Space Complexity', include the Big O notation **inside <code> tags**, placed directly beside the <h4> heading (e.g., <h4>Time Complexity <code>O(N)</code></h4>).\n" +
                            "3. Provide clear, concise explanations in <p> tags immediately below each heading.\n" +
                            "4. If the code is not optimal, add an additional section:\n" +
                            "   <h4>Optimal Approach</h4>\n" +
                            "   <p>Describe a more efficient solution in plain language and include its improved Time and Space complexities (also inside <code> tags).</p>\n" +
                            "5. Do not include any markdown syntax, code fences, or full code snippets in your response.\n" +
                            "6. Avoid extra line breaks, indentation, or unnecessary whitespace between HTML elements. The final output should be compact, valid HTML.\n\n" +

                            "Your analysis should highlight algorithmic behavior, data structure usage, and any trade-offs clearly and professionally.\n\n" +

                            "Code to analyze:\n```" + language + "\n" + code + "\n```";

                        
                } else if ("explain".equalsIgnoreCase(type)) {
                    
                    String contextSegment = "";
                    if (!executionContext.isEmpty()) {
                        contextSegment = 
                            "The following console output and user inputs were recorded during the code’s execution:<br/>" +
                            "<pre>" + executionContext + "</pre>" +
                            "Use this execution history to clarify how the program behaved, especially if any errors or unexpected outputs occurred. " +
                            "If a runtime or logical error is detected, clearly explain its cause and what part of the code led to it.";
                    }

                    // EXPLAIN PROMPT: Structured, HTML-based explanation
                    prompt = 
                        "You are an experienced programming instructor. Your task is to provide a clear, step-by-step explanation of how the following " + language + 
                        " code works. The goal is to help learners understand what each part of the code does and how the program executes overall.\n\n" +

                        "Your response must be a single, well-structured block of valid HTML — ready to be rendered directly inside a webpage. " +
                        "Follow these exact rules for formatting:\n" +
                        "1. Use <h4> for section headings such as 'Overview', 'Step-by-Step Execution', and 'Key Takeaways'.\n" +
                        "2. Inside the step-by-step section, explain major operations in logical order (e.g., initialization, loops, condition checks, function calls, I/O handling).\n" +
                        "3. Use <p> tags for your explanations — make them concise, factual, and easy to understand.\n" +
                        "4. Use <code> tags to highlight keywords, variable names, values, and important expressions (e.g., 'the variable <code>x</code> becomes <code>5</code>').\n" +
                        "5. If the code produces output or an error, include a short section <h4>Program Output</h4> summarizing the final result or cause of failure.\n" +
                        "6. Do NOT include markdown, code fences, or unnecessary spacing. The HTML must be compact, semantic, and visually clean.\n" +
                        "7. Avoid storytelling — focus on clarity, correctness, and logical flow.\n\n" +

                        "The explanation should be written in an approachable yet technically accurate tone, focusing on what happens and why.\n\n" +
                        (executionContext.isEmpty() ? "" : contextSegment) +
                        "\n\nHere is the " + language + " code to explain:\n```" + language + "\n" + code + "\n```";



            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("result", "Error: Unknown Gemini request type: " + type));
            }

            RestTemplate restTemplate = new RestTemplate();

            Map<String, Object> request = new HashMap<>();
            Map<String, String> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(textPart));
            request.put("contents", List.of(content));

            // Construct the final URL with the API key
            String fullUrl = GEMINI_URL + GEMINI_API_KEY;

            ResponseEntity<Map> response = restTemplate.postForEntity(
                fullUrl, request, Map.class);

            String output = "No response from Gemini";
            if (response.getBody() != null && response.getStatusCode() == HttpStatus.OK) {
                try {
    Object candidatesObj = response.getBody().get("candidates");
    if (candidatesObj instanceof List candidates && !candidates.isEmpty()) {
        Object firstCandidateObj = candidates.get(0);
        if (firstCandidateObj instanceof Map firstCandidate) {
            Object contentObjRaw = firstCandidate.get("content");
            if (contentObjRaw instanceof Map contentObj) {
                Object partsObj = contentObj.get("parts");
                if (partsObj instanceof List parts && !parts.isEmpty()) {
                    Object firstPartObj = parts.get(0);
                    if (firstPartObj instanceof Map firstPart) {
                        Object textObj = firstPart.get("text");
                        if (textObj instanceof String text) {
                            output = text;
                        } else {
                            output = "Error: 'text' field missing or not a string.";
                        }
                    } else {
                        output = "Error: 'parts' first element is not a valid Map.";
                    }
                } else {
                    output = "Error: 'parts' list is empty or not a List.";
                }
            } else {
                output = "Error: 'content' is missing or not a Map.";
            }
        } else {
            output = "Error: First candidate is not a Map.";
        }
    } else {
        output = "Error: No candidates returned or list is empty.";
    }
} catch (Exception e) {
    output = "Error parsing Gemini response: " + e.getMessage();
    System.err.println("❌ Error parsing response: " + e.getMessage());
}

            } else if (response.getStatusCode() != HttpStatus.OK) {
                output = "Gemini API returned status: " + response.getStatusCode();
            }

            return ResponseEntity.ok(Map.of("result", output));

        } catch (Exception e) {
            System.err.println("❌ Error in Gemini controller: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "Error: " + e.getMessage()));
        }
    }
}
