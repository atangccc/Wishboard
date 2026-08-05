package cn.aobp.wishboard;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.TemplateNameResolver;
import java.util.HashMap;
import java.util.Map;

/**
 * 便签墙页面路由。
 * <p>
 * 当插件设置中 basic.enableBuiltinPage 为 true 时，注册 GET /wishes 路由，
 * 由插件直接渲染便签墙页面（注入插件设置到模板 model）。
 * <p>
 * 默认关闭（false），此时不拦截 /wishes 请求，由 Halo 的 SinglePage 机制处理。
 * 用户需在后台创建自定义页面（模板选「便签墙」，别名设为 wishes）。
 * 这样即使插件停用，页面仍可访问并显示「需要安装插件」提示，而非 404。
 */
@Component
@RequiredArgsConstructor
public class WishPageRouter implements RouterFunction<ServerResponse> {

    private final TemplateNameResolver templateNameResolver;
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    @NonNull
    public Mono<HandlerFunction<ServerResponse>> route(@NonNull ServerRequest request) {
        // 仅匹配 GET /wishes
        if (!"GET".equalsIgnoreCase(request.method().name())) {
            return Mono.empty();
        }
        String path = request.requestPath().pathWithinApplication().value();
        if (!"/wishes".equals(path)) {
            return Mono.empty();
        }
        // 动态读取设置：是否启用插件内置页面
        return setting("basic")
            .flatMap(basic -> {
                boolean enabled = SettingValues.bool(basic, "enableBuiltinPage", false);
                if (!enabled) {
                    // 不启用内置页面，返回空让 SinglePage 路由接管
                    return Mono.empty();
                }
                return Mono.just((HandlerFunction<ServerResponse>) this::renderWishesPage);
            });
    }

    @Override
    public void accept(@NonNull RouterFunctions.Visitor visitor) {
        // no-op
    }

    Mono<ServerResponse> renderWishesPage(ServerRequest request) {
        var basicMono = setting("basic");
        var treeholeMono = setting("treehole");
        var aiMono = setting("ai");

        return Mono.zip(basicMono, treeholeMono, aiMono)
            .map(tuple -> {
                var basic = tuple.getT1();
                var treehole = tuple.getT2();
                var ai = tuple.getT3();

                var model = new HashMap<String, Object>();
                model.put("pageTitle", SettingValues.text(basic, "pageTitle", "心愿墙"));
                model.put("pageSubtitle", SettingValues.text(basic, "pageSubtitle", "写下你的心愿，留下你的故事"));
                model.put("showDaysCounter", SettingValues.bool(basic, "showDaysCounter", false));
                model.put("anniversaryDate", SettingValues.text(basic, "anniversaryDate", ""));
                model.put("partnerNameA", SettingValues.text(basic, "partnerNameA", ""));
                model.put("partnerNameB", SettingValues.text(basic, "partnerNameB", ""));
                model.put("maxContentLength", SettingValues.integer(treehole, "maxLength", 200));
                model.put("enableSubmit", SettingValues.bool(treehole, "enableSubmit", true));
                model.put("aiEnabled", SettingValues.bool(ai, "enabled", false));
                return model;
            })
            .flatMap(model ->
                templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "wishes")
                    .flatMap(tpl -> ServerResponse.ok().render(tpl, model))
            );
    }

    @SuppressWarnings("unchecked")
    private Mono<Object> setting(String group) {
        return ((Mono<Object>) (Mono<?>) settingFetcher.getSettingValue(group))
            .defaultIfEmpty(Map.of())
            .onErrorReturn(Map.of());
    }
}
