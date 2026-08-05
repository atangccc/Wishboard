package cn.aobp.wishboard.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "wishboard.aobp.cn",
     version = "v1alpha1",
     kind = "WishType",
     plural = "wishtypes",
     singular = "wishtype")
public class WishType extends AbstractExtension {

    private WishTypeSpec spec;

    @Data
    public static class WishTypeSpec {
        /** 类型标识，如 wish / treehole / custom-xxx */
        private String slug;
        /** 显示名称，如 心愿 / 树洞 */
        private String displayName;
        /** 描述 */
        private String description;
        /** 是否内置类型（内置不可删除） */
        private boolean builtIn;
        /** 排序权重，越小越靠前 */
        private int priority;
    }
}
