package com.joinlivora.backend.streaming.chat.commands;

public interface ChatCommandHandler {
    boolean supports(String command);
    void execute(ChatCommandContext context);
}
