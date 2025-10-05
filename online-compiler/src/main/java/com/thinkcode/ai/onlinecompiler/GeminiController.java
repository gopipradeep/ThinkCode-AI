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
                prompt = "As an expert code analyst, provide a concise complexity analysis for the following " + language + " code. "
                       + "Your response must be a single, clean block of HTML. "
                       + "For 'Time Complexity' and 'Space Complexity' sections, include the Big O notation using <code> tags directly beside the <h4> heading (e.g., <h4>Time Complexity <code>O(N)</code></h4>) and then provide the explanation in a separate <p> tag. "
                       + "Use <h4> for all headings. Use <p> for explanations. "
                       + "If the provided code is not optimal, you MUST include a section with the heading <h4>Optimal Approach</h4>. "
                       + "In this section, explain the better approach in words and provide its Time and Space Complexity (in <code> tags). **Do not provide the full optimized code snippet.** "
                       + "Do not use markdown. Ensure there are no extra line breaks or spacing between HTML elements. The entire response should be compact and ready to be injected directly into a div."
                       + "\n\nCode to analyze:\n```" + language + "\n"
                       + code + "\n```";
                       
            } else if ("explain".equalsIgnoreCase(type)) {
                
                String contextSegment = "";
                if (!executionContext.isEmpty()) {
                    contextSegment = "The user has run this code with the following console output and inputs:\n<pre>" 
                                   + executionContext
                                   + "</pre>This execution history must be central to your explanation, especially if an error (like a runtime crash) occurred due to input data. You must diagnose and explain the error.";
                }

                // EXPLAIN PROMPT: Strict HTML output (Beginner-friendly)
                prompt = "You are a friendly and helpful programming tutor. Your task is to explain the execution of a piece of code to a beginner. "
                       + "Describe the code's journey step-by-step, as if you were telling a story. "
                       + "Your response must be a single, clean block of HTML. "
                       + "Use <h4> for headings for each major step (e.g., 'Step 1: Setting Things Up'). "
                       + "Use <p> for your explanations. "
                       + "Use <code> tags to highlight variable names and their values (e.g., 'the variable <code>count</code> is now <code>5</code>'). "
                       + "Do not use markdown. The entire response should be compact and easy to read. "
                       + (executionContext.isEmpty() ? "" : contextSegment)
                       + "\n\nHere is the " + language + " code to explain:\n```" + language + "\n"
                       + code + "\n```";

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
