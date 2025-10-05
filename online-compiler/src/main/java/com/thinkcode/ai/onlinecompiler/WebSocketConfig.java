package com.thinkcode.ai.onlinecompiler;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InteractiveCodeExecutionHandler interactiveCodeExecutionHandler;

    public WebSocketConfig(InteractiveCodeExecutionHandler interactiveCodeExecutionHandler) {
        this.interactiveCodeExecutionHandler = interactiveCodeExecutionHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interactiveCodeExecutionHandler, "/execute-ws")
                .setAllowedOrigins("*");
    }
}
