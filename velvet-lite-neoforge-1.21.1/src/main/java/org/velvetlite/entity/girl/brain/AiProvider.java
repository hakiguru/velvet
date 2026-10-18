package org.velvetlite.entity.girl.brain;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/**
 * Where a girl's words come from: her own built-in brain, or a language model. Every model service speaks one
 * of two protocols: Anthropic's Messages API, or the OpenAI chat completions API that most others (and the
 * local servers) copy. The model lists are only suggestions: the editor can load the real list from the
 * service, and any model id can be typed in.
 */
public enum AiProvider {
    INTERNAL("internal", "Internal", Protocol.NONE, "", false, false, List.of(), ""),
    CLAUDE("anthropic", "Claude", Protocol.ANTHROPIC, "https://api.anthropic.com/v1", true, false,
            List.of("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1"),
            "https://console.anthropic.com/settings/keys"),
    CHATGPT("openai", "ChatGPT", Protocol.OPENAI, "https://api.openai.com/v1", true, false,
            List.of("gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1-mini"),
            "https://platform.openai.com/api-keys"),
    GEMINI("gemini", "Gemini", Protocol.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", true, false,
            List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
            "https://aistudio.google.com/apikey"),
    DEEPSEEK("deepseek", "DeepSeek", Protocol.OPENAI, "https://api.deepseek.com/v1", true, false,
            List.of("deepseek-chat", "deepseek-reasoner"),
            "https://platform.deepseek.com/api_keys"),
    QWEN("qwen", "Qwen", Protocol.OPENAI, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", true, false,
            List.of("qwen-max", "qwen-plus", "qwen-turbo"),
            "https://modelstudio.console.alibabacloud.com/"),
    GROK("xai", "Grok", Protocol.OPENAI, "https://api.x.ai/v1", true, false,
            List.of("grok-4", "grok-3-mini"),
            "https://console.x.ai/"),
    MISTRAL("mistral", "Mistral", Protocol.OPENAI, "https://api.mistral.ai/v1", true, false,
            List.of("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest"),
            "https://console.mistral.ai/api-keys"),
    OPENROUTER("openrouter", "OpenRouter", Protocol.OPENAI, "https://openrouter.ai/api/v1", true, false,
            List.of("openrouter/auto"),
            "https://openrouter.ai/keys"),
    OLLAMA("ollama", "Ollama", Protocol.OPENAI, "http://localhost:11434/v1", false, true,
            List.of("llama3.1", "qwen2.5", "gemma2", "mistral"),
            "https://ollama.com/download"),
    LM_STUDIO("lmstudio", "LM Studio", Protocol.OPENAI, "http://localhost:1234/v1", false, true,
            List.of(),
            "https://lmstudio.ai/"),
    CUSTOM("custom", "OpenAI-compatible", Protocol.OPENAI, "", false, true,
            List.of(),
            "");

    public enum Protocol { NONE, ANTHROPIC, OPENAI }

    /** As saved in behavior.json ("brain" → "provider"). */
    public final String id;
    /** The service's own name, the same in every language. */
    public final String title;
    public final Protocol protocol;
    /** Address of the API, up to the version ("…/v1"); the endpoint can replace it for local ones. */
    public final String baseUrl;
    /** Needs an API key (local servers don't, a custom one may). */
    public final boolean needsKey;
    /** Runs on a computer of the player's (or the server's): its address can be changed. */
    public final boolean local;
    public final List<String> suggestedModels;
    /** Where to get a key (or the program). */
    public final String setupUrl;

    AiProvider(String id, String title, Protocol protocol, String baseUrl, boolean needsKey, boolean local,
               List<String> suggestedModels, String setupUrl) {
        this.id = id;
        this.title = title;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.needsKey = needsKey;
        this.local = local;
        this.suggestedModels = suggestedModels;
        this.setupUrl = setupUrl;
    }

    public boolean isExternal() {
        return protocol != Protocol.NONE;
    }

    /** The model used when none is picked. */
    public String defaultModel() {
        return suggestedModels.isEmpty() ? "" : suggestedModels.getFirst();
    }

    /** Translation key of the short how-to-connect text in the editor. */
    public String setupKey() {
        return "editor.velvet_lite.ai.setup." + id;
    }

    @Nullable
    public static AiProvider byId(String id) {
        for (AiProvider p : values()) if (p.id.equalsIgnoreCase(id.trim())) return p;
        return null;
    }

    public static String idList() {
        StringJoiner j = new StringJoiner(", ");
        for (AiProvider p : values()) j.add(p.id);
        return j.toString();
    }
}
