package com.thinkcode.ai.onlinecompiler;

public class CodeRequest {
    private String code;
    private String language;
    
    // Default constructor
    public CodeRequest() {}
    
    // Constructor with parameters
    public CodeRequest(String code, String language) {
        this.code = code;
        this.language = language;
    }
    
    // Getters
    public String getCode() {
        return code;
    }
    
    public String getLanguage() {
        return language;
    }
    
    // Setters
    public void setCode(String code) {
        this.code = code;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    @Override
    public String toString() {
        return "CodeRequest{" +
                "language='" + language + '\'' +
                ", codeLength=" + (code != null ? code.length() : 0) +
                '}';
    }
}
