package cn.aobp.wishboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import cn.aobp.wishboard.model.Wish;
import cn.aobp.wishboard.model.WishType;

import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishTypeService {

    private final ReactiveExtensionClient client;

    public Flux<WishType> listAll() {
        return client.listAll(WishType.class, ListOptions.builder().build(), null)
            .sort(Comparator.comparingInt(t -> t.getSpec().getPriority()));
    }

    public Mono<WishType> create(WishType type) {
        type.getMetadata().setName(UUID.randomUUID().toString());
        return client.create(type);
    }

    public Mono<WishType> update(WishType type) {
        return client.update(type);
    }

    public Mono<Void> delete(String name) {
        return client.get(WishType.class, name)
            .flatMap(client::delete)
            .then();
    }

    public Mono<WishType> getByName(String name) {
        return client.get(WishType.class, name);
    }

    /**
     * 初始化内置类型（如果不存在）
     */
    public Mono<Void> initBuiltInTypes() {
        return listAll().collectList().flatMap(existing -> {
            boolean hasWish = existing.stream()
                .anyMatch(t -> "wish".equals(t.getSpec().getSlug()));
            boolean hasTreehole = existing.stream()
                .anyMatch(t -> "treehole".equals(t.getSpec().getSlug()));

            Mono<Void> createWish = Mono.empty();
            Mono<Void> createTreehole = Mono.empty();

            if (!hasWish) {
                WishType wish = new WishType();
                wish.setSpec(new WishType.WishTypeSpec());
                wish.getSpec().setSlug("wish");
                wish.getSpec().setDisplayName("心愿");
                wish.getSpec().setDescription("许下心愿，期待实现");
                wish.getSpec().setBuiltIn(true);
                wish.getSpec().setPriority(0);
                wish.setMetadata(new run.halo.app.extension.Metadata());
                createWish = create(wish).then();
            }
            if (!hasTreehole) {
                WishType treehole = new WishType();
                treehole.setSpec(new WishType.WishTypeSpec());
                treehole.getSpec().setSlug("treehole");
                treehole.getSpec().setDisplayName("树洞");
                treehole.getSpec().setDescription("匿名倾诉，温暖回应");
                treehole.getSpec().setBuiltIn(true);
                treehole.getSpec().setPriority(1);
                treehole.setMetadata(new run.halo.app.extension.Metadata());
                createTreehole = create(treehole).then();
            }
            return createWish.then(createTreehole);
        });
    }

    /**
     * 统计某个类型的便签数量
     */
    public Mono<Long> countByType(String typeSlug) {
        return client.listAll(Wish.class, ListOptions.builder().build(), null)
            .filter(w -> typeSlug.equals(w.getSpec().getType()))
            .count();
    }
}
