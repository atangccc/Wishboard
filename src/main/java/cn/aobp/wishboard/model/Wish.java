package cn.aobp.wishboard.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "wishboard.aobp.cn",
     version = "v1alpha1",
     kind = "Wish",
     plural = "wishes",
     singular = "wish")
public class Wish extends AbstractExtension {

    private WishSpec spec;

    @Data
    public static class WishSpec {
        /** 便签内容 */
        private String content;
        /** 昵称（访客填写，站长用系统用户名） */
        private String nickname;
        /** wish=心愿, treehole=树洞 */
        private String type;
        /** 关联的 Halo 用户名（访客为空） */
        private String author;
        /** 便签颜色: pink/blue/yellow/green/purple */
        private String color;
        /**
         * 心愿状态: pending/doing/done
         * 树洞状态: approved/pending_review/rejected
         */
        private String status;
        /** 是否匿名 */
        private boolean anonymous;
        /** AI 暖心回复 */
        private String aiReply;
        /** AI 情绪标签 emoji */
        private String emotionTag;
        /** 访客 IP（不展示，仅防刷） */
        private String ip;
        /** 心愿达成后的纪念照片 */
        private String doneImage;
        /** 心愿达成感言 */
        private String doneNote;
        /** 优先级: normal/important/urgent */
        private String priority;
        /** 创建时间 */
        private Instant createdAt;
        /** 完成时间 */
        private Instant completedAt;
    }
}
