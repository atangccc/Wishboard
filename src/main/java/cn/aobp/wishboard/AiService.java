package cn.aobp.wishboard;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/** AI features backed by the optional Halo AI Foundation plugin. */
@Slf4j
@Service
public class AiService {
    private final ExtensionGetter extensionGetter;

    public AiService(ExtensionGetter extensionGetter) {
        this.extensionGetter = extensionGetter;
    }

    private Mono<String> generate(String content, String systemPrompt) {
        return Mono.defer(() -> {
            try {
                Class<?> serviceType = Class.forName("run.halo.aifoundation.AiModelService");
                Object requestBuilder = invokeStatic(
                    "run.halo.aifoundation.chat.GenerateTextRequest", "builder");
                invoke(requestBuilder, "system", systemPrompt);
                invoke(requestBuilder, "prompt", content);
                invoke(requestBuilder, "temperature", 0.2d);
                invoke(requestBuilder, "maxOutputTokens", 256);
                Object request = invoke(requestBuilder, "build");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Mono<Object> extensionMono = (Mono<Object>) (Mono<?>)
                    extensionGetter.getEnabledExtension((Class) serviceType);
                return extensionMono
                    .switchIfEmpty(Mono.error(
                        new IllegalStateException("AI Foundation 未启用")))
                    .flatMap(extension -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Mono<Object> modelMono = (Mono<Object>) invoke(extension,
                                "languageModel");
                            return modelMono;
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    })
                    .flatMap(model -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Mono<Object> result = (Mono<Object>) invoke(model,
                                "generateText", request);
                            return result;
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    })
                    .map(value -> {
                        try {
                            Object text = invoke(value, "getText");
                            return text == null ? "" : text.toString();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
            } catch (ClassNotFoundException e) {
                return Mono.error(new IllegalStateException("AI Foundation 未安装", e));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && matches(method.getParameterTypes(), args)) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) {
                if (parameterTypes[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> parameterType = wrap(parameterTypes[i]);
            if (!parameterType.isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object invokeStatic(String className, String methodName) throws Exception {
        Class<?> type = Class.forName(className);
        return type.getMethod(methodName).invoke(null);
    }

    public Mono<String> generateWarmReply(String content, String systemPrompt) {
        return generate(content, systemPrompt).onErrorResume(e -> {
            log.warn("[Wishboard AI] Warm reply failed: {}", e.getMessage());
            return Mono.just("");
        });
    }

    public Mono<String> detectEmotion(String content) {
        return generate(content, "分析以下文字的情绪，只返回一个最合适的emoji，不要返回其他任何内容。")
            .map(String::trim).onErrorResume(e -> Mono.just("💭"));
    }

    public Mono<Boolean> reviewContent(String content, String nickname) {
        String prompt = "你是内容审核员。判断以下用户的昵称和留言内容是否包含违规内容（色情、暴力、广告、政治敏感、人身攻击、不雅词汇）。只回答 PASS 或 REJECT，不要解释。";
        return generate("昵称: " + nickname + "\n内容: " + content, prompt)
            .map(reply -> reply.trim().toUpperCase().contains("PASS"))
            .onErrorResume(e -> Mono.just(false));
    }
}
