package cn.aobp.wishboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

import cn.aobp.wishboard.model.WishType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

/**
 * 管理接口（需要登录 + 管理权限）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WishRouter implements CustomEndpoint {

    private final WishService wishService;
    private final WishTypeService wishTypeService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.wishboard.aobp.cn/v1alpha1/Wishboard";
        return SpringdocRouteBuilder.route()
            .GET("wishes", this::listAll,
                b -> b.operationId("ListAllWishes").tag(tag)
                    .description("管理员获取全部便签")
                    .response(responseBuilder().description("全部便签")))
            .GET("wishes/pending", this::listPending,
                b -> b.operationId("ListPendingWishes").tag(tag)
                    .description("获取待审核便签")
                    .response(responseBuilder().description("待审核列表")))
            .POST("wishes/{name}/approve", this::approve,
                b -> b.operationId("ApproveWish").tag(tag)
                    .description("审核通过便签")
                    .response(responseBuilder().description("审核结果")))
            .POST("wishes/{name}/reject", this::reject,
                b -> b.operationId("RejectWish").tag(tag)
                    .description("审核拒绝便签")
                    .response(responseBuilder().description("审核结果")))
            .PUT("wishes/{name}", this::updateWish,
                b -> b.operationId("UpdateWish").tag(tag)
                    .description("更新便签")
                    .response(responseBuilder().description("更新结果")))
            .DELETE("wishes/{name}", this::deleteWish,
                b -> b.operationId("DeleteWish").tag(tag)
                    .description("删除便签")
                    .response(responseBuilder().description("删除结果")))
            .POST("wishes/-/batch-delete", this::batchDeleteWishes,
                b -> b.operationId("BatchDeleteWishes").tag(tag)
                    .description("批量删除便签")
                    .response(responseBuilder().description("批量删除结果")))
            .GET("wish-types", this::listTypes,
                b -> b.operationId("ListWishTypes").tag(tag)
                    .description("获取所有便签类型")
                    .response(responseBuilder().description("类型列表")))
            .POST("wish-types", this::createType,
                b -> b.operationId("CreateWishType").tag(tag)
                    .description("创建便签类型")
                    .response(responseBuilder().description("创建结果")))
            .PUT("wish-types/{name}", this::updateType,
                b -> b.operationId("UpdateWishType").tag(tag)
                    .description("更新便签类型")
                    .response(responseBuilder().description("更新结果")))
            .DELETE("wish-types/{name}", this::deleteType,
                b -> b.operationId("DeleteWishType").tag(tag)
                    .description("删除便签类型")
                    .response(responseBuilder().description("删除结果")))
            .GET("wish-types/-/stats", this::typeStats,
                b -> b.operationId("WishTypeStats").tag(tag)
                    .description("获取各类型便签数量统计")
                    .response(responseBuilder().description("统计数据")))
            .GET("wishes/-/export", this::exportData,
                b -> b.operationId("ExportWishData").tag(tag)
                    .description("导出所有便签和类型数据")
                    .response(responseBuilder().description("JSON 数据")))
            .POST("wishes/-/import", this::importData,
                b -> b.operationId("ImportWishData").tag(tag)
                    .description("导入便签和类型数据")
                    .response(responseBuilder().description("导入结果")))
            .build();
    }

    private Mono<ServerResponse> listAll(ServerRequest request) {
        return wishService.listAll()
            .collectList()
            .flatMap(list -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(list));
    }

    private Mono<ServerResponse> listPending(ServerRequest request) {
        return wishService.listPendingReview()
            .collectList()
            .flatMap(list -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(list));
    }

    private Mono<ServerResponse> approve(ServerRequest request) {
        String name = request.pathVariable("name");
        return wishService.get(name).flatMap(wish -> {
            wish.getSpec().setStatus("approved");
            return wishService.update(wish);
        }).flatMap(w -> ServerResponse.ok().bodyValue(Map.of("message", "已通过")));
    }

    private Mono<ServerResponse> reject(ServerRequest request) {
        String name = request.pathVariable("name");
        return wishService.get(name).flatMap(wish -> {
            wish.getSpec().setStatus("rejected");
            return wishService.update(wish);
        }).flatMap(w -> ServerResponse.ok().bodyValue(Map.of("message", "已拒绝")));
    }

    @SuppressWarnings("unchecked")
    private Mono<ServerResponse> updateWish(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(Map.class).flatMap(body ->
            wishService.get(name).flatMap(wish -> {
                var spec = wish.getSpec();
                if (body.containsKey("status")) spec.setStatus((String) body.get("status"));
                if (body.containsKey("content")) spec.setContent((String) body.get("content"));
                if (body.containsKey("color")) spec.setColor((String) body.get("color"));
                if (body.containsKey("priority")) spec.setPriority((String) body.get("priority"));
                if (body.containsKey("doneImage")) spec.setDoneImage((String) body.get("doneImage"));
                if (body.containsKey("doneNote")) spec.setDoneNote((String) body.get("doneNote"));
                if ("done".equals(body.get("status")) && spec.getCompletedAt() == null) {
                    spec.setCompletedAt(Instant.now());
                }
                return wishService.update(wish);
            }).flatMap(w -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON).bodyValue(w))
        );
    }

    private Mono<ServerResponse> deleteWish(ServerRequest request) {
        String name = request.pathVariable("name");
        return wishService.delete(name)
            .then(ServerResponse.ok().bodyValue(Map.of("message", "已删除")));
    }

    @SuppressWarnings("unchecked")
    private Mono<ServerResponse> batchDeleteWishes(ServerRequest request) {
        return request.bodyToMono(Map.class).flatMap(body -> {
            var names = (java.util.List<String>) body.get("names");
            if (names == null || names.isEmpty()) {
                return ServerResponse.badRequest()
                    .bodyValue(Map.of("error", "请提供要删除的便签列表"));
            }
            return wishService.batchDelete(names)
                .then(ServerResponse.ok()
                    .bodyValue(Map.of("message", "已批量删除 " + names.size() + " 条便签")));
        });
    }

    // ===== 类型管理 =====

    private Mono<ServerResponse> listTypes(ServerRequest request) {
        return wishTypeService.listAll()
            .collectList()
            .flatMap(list -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(list));
    }

    @SuppressWarnings("unchecked")
    private Mono<ServerResponse> createType(ServerRequest request) {
        return request.bodyToMono(Map.class).flatMap(body -> {
            String slug = ((String) body.getOrDefault("slug", "")).trim()
                .replaceAll("[^a-zA-Z0-9_-]", "");
            String displayName = ((String) body.getOrDefault("displayName", "")).trim()
                .replaceAll("<[^>]*>", "");
            String description = ((String) body.getOrDefault("description", "")).trim()
                .replaceAll("<[^>]*>", "");
            int priority = body.containsKey("priority")
                ? ((Number) body.get("priority")).intValue() : 10;

            if (slug.isBlank() || displayName.isBlank()) {
                return ServerResponse.badRequest()
                    .bodyValue(Map.of("error", "标识和名称不能为空"));
            }
            if (slug.length() > 50 || displayName.length() > 50) {
                return ServerResponse.badRequest()
                    .bodyValue(Map.of("error", "标识和名称不能超过50字"));
            }

            // 检查 slug 是否重复
            return wishTypeService.listAll()
                .filter(t -> slug.equals(t.getSpec().getSlug()))
                .hasElements()
                .flatMap(exists -> {
                    if (exists) {
                        return ServerResponse.badRequest()
                            .bodyValue(Map.of("error", "类型标识已存在"));
                    }
                    WishType type = new WishType();
                    type.setSpec(new WishType.WishTypeSpec());
                    type.getSpec().setSlug(slug);
                    type.getSpec().setDisplayName(displayName);
                    type.getSpec().setDescription(description);
                    type.getSpec().setBuiltIn(false);
                    type.getSpec().setPriority(priority);
                    type.setMetadata(new run.halo.app.extension.Metadata());
                    return wishTypeService.create(type)
                        .flatMap(created -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(created));
                });
        });
    }

    @SuppressWarnings("unchecked")
    private Mono<ServerResponse> updateType(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(Map.class).flatMap(body ->
            wishTypeService.listAll()
                .filter(t -> t.getMetadata().getName().equals(name))
                .next()
                .flatMap(type -> {
                    if (body.containsKey("displayName")) {
                        type.getSpec().setDisplayName((String) body.get("displayName"));
                    }
                    if (body.containsKey("description")) {
                        type.getSpec().setDescription((String) body.get("description"));
                    }
                    if (body.containsKey("priority")) {
                        type.getSpec().setPriority(((Number) body.get("priority")).intValue());
                    }
                    return wishTypeService.update(type)
                        .flatMap(updated -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(updated));
                })
                .switchIfEmpty(ServerResponse.notFound().build())
        );
    }

    private Mono<ServerResponse> deleteType(ServerRequest request) {
        String name = request.pathVariable("name");
        // 先获取类型 slug，级联删除该类型下所有便签，再删除类型
        return wishTypeService.getByName(name)
            .flatMap(type -> {
                String slug = type.getSpec().getSlug();
                return wishService.deleteByType(slug)
                    .then(wishTypeService.delete(name))
                    .then(ServerResponse.ok().bodyValue(
                        Map.of("message", "已删除类型及其下所有便签")));
            })
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> typeStats(ServerRequest request) {
        return wishTypeService.listAll()
            .flatMap(type -> wishTypeService.countByType(type.getSpec().getSlug())
                .map(count -> Map.of(
                    "slug", type.getSpec().getSlug(),
                    "displayName", type.getSpec().getDisplayName(),
                    "count", count
                )))
            .collectList()
            .flatMap(stats -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stats));
    }

    private Mono<ServerResponse> exportData(ServerRequest request) {
        var wishesMono = wishService.listAll().collectList();
        var typesMono = wishTypeService.listAll().collectList();
        return wishesMono.zipWith(typesMono).flatMap(tuple -> {
            var wishes = tuple.getT1().stream().map(w -> Map.of(
                "content", nullSafe(w.getSpec().getContent()),
                "nickname", nullSafe(w.getSpec().getNickname()),
                "type", nullSafe(w.getSpec().getType()),
                "color", nullSafe(w.getSpec().getColor()),
                "status", nullSafe(w.getSpec().getStatus()),
                "anonymous", w.getSpec().isAnonymous(),
                "aiReply", nullSafe(w.getSpec().getAiReply()),
                "emotionTag", nullSafe(w.getSpec().getEmotionTag()),
                "priority", nullSafe(w.getSpec().getPriority()),
                "createdAt", w.getSpec().getCreatedAt() != null ? w.getSpec().getCreatedAt().toString() : ""
            )).toList();
            var types = tuple.getT2().stream().map(t -> Map.of(
                "slug", nullSafe(t.getSpec().getSlug()),
                "displayName", nullSafe(t.getSpec().getDisplayName()),
                "description", nullSafe(t.getSpec().getDescription()),
                "builtIn", t.getSpec().isBuiltIn(),
                "priority", t.getSpec().getPriority()
            )).toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("wishes", wishes, "types", types,
                    "exportedAt", Instant.now().toString(), "version", "1.0"));
        });
    }

    private String nullSafe(String s) { return s != null ? s : ""; }

    @SuppressWarnings("unchecked")
    private Mono<ServerResponse> importData(ServerRequest request) {
        return request.bodyToMono(Map.class).flatMap(body -> {
            var wishesList = (java.util.List<Map<String, Object>>) body.getOrDefault("wishes", java.util.List.of());
            var typesList = (java.util.List<Map<String, Object>>) body.getOrDefault("types", java.util.List.of());

            // 先导入类型
            Mono<Void> importTypes = reactor.core.publisher.Flux.fromIterable(typesList)
                .flatMap(tm -> {
                    String slug = (String) tm.getOrDefault("slug", "");
                    if (slug.isBlank()) return Mono.empty();
                    // 检查是否已存在
                    return wishTypeService.listAll()
                        .filter(t -> slug.equals(t.getSpec().getSlug()))
                        .hasElements()
                        .flatMap(exists -> {
                            if (exists) return Mono.empty();
                            var wt = new cn.aobp.wishboard.model.WishType();
                            wt.setSpec(new cn.aobp.wishboard.model.WishType.WishTypeSpec());
                            wt.getSpec().setSlug(slug);
                            wt.getSpec().setDisplayName((String) tm.getOrDefault("displayName", slug));
                            wt.getSpec().setDescription((String) tm.getOrDefault("description", ""));
                            wt.getSpec().setBuiltIn(Boolean.TRUE.equals(tm.get("builtIn")));
                            wt.getSpec().setPriority(tm.containsKey("priority") ? ((Number) tm.get("priority")).intValue() : 10);
                            wt.setMetadata(new run.halo.app.extension.Metadata());
                            return wishTypeService.create(wt).then();
                        });
                }).then();

            // 再导入便签
            Mono<Long> importWishes = importTypes.then(
                reactor.core.publisher.Flux.fromIterable(wishesList)
                    .flatMap(wm -> {
                        String content = (String) wm.getOrDefault("content", "");
                        if (content.isBlank()) return Mono.empty();
                        var wish = new cn.aobp.wishboard.model.Wish();
                        wish.setSpec(new cn.aobp.wishboard.model.Wish.WishSpec());
                        wish.getSpec().setContent(content);
                        wish.getSpec().setNickname((String) wm.getOrDefault("nickname", "匿名"));
                        wish.getSpec().setType((String) wm.getOrDefault("type", "treehole"));
                        wish.getSpec().setColor((String) wm.getOrDefault("color", "green"));
                        wish.getSpec().setStatus((String) wm.getOrDefault("status", "approved"));
                        wish.getSpec().setAnonymous(Boolean.TRUE.equals(wm.get("anonymous")));
                        wish.getSpec().setAiReply((String) wm.getOrDefault("aiReply", ""));
                        wish.getSpec().setEmotionTag((String) wm.getOrDefault("emotionTag", ""));
                        wish.getSpec().setPriority((String) wm.getOrDefault("priority", "normal"));
                        String createdAt = (String) wm.getOrDefault("createdAt", "");
                        if (!createdAt.isBlank()) {
                            try { wish.getSpec().setCreatedAt(Instant.parse(createdAt)); }
                            catch (Exception e) { wish.getSpec().setCreatedAt(Instant.now()); }
                        } else {
                            wish.getSpec().setCreatedAt(Instant.now());
                        }
                        wish.setMetadata(new run.halo.app.extension.Metadata());
                        return wishService.create(wish);
                    }).count()
            );

            return importWishes.flatMap(count ->
                ServerResponse.ok().bodyValue(Map.of(
                    "message", "导入成功",
                    "importedWishes", count,
                    "importedTypes", typesList.size()
                ))
            );
        });
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.wishboard.aobp.cn/v1alpha1");
    }
}
