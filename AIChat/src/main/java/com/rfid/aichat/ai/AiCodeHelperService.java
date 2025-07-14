package com.rfid.aichat.ai;

import com.rfid.aichat.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.service.*;
import dev.langchain4j.service.guardrail.InputGuardrails;
import reactor.core.publisher.Flux;

import java.util.List;

//改为手动构建，更灵活
//@AiService
@InputGuardrails({SafeInputGuardrail.class})
public interface AiCodeHelperService {
    // 流式对话
    @SystemMessage(fromResource = "system-prompt.txt")
    Flux<String> chatStream(@MemoryId int memoryId, @UserMessage String userMessage);
}
