package cn.aobp.wishboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import cn.aobp.wishboard.model.Wish;
import cn.aobp.wishboard.model.WishType;

@Slf4j
@Component
public class WishboardPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public WishboardPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Wish.class);
        schemeManager.register(WishType.class);

    }

    @Override
    public void stop() {
        schemeManager.unregister(schemeManager.get(Wish.class));
        schemeManager.unregister(schemeManager.get(WishType.class));
        log.info("[Wishboard] Plugin stopped");
    }
}
