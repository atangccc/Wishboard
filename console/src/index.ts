import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import { IconSettings } from "@halo-dev/components";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/wishboard",
        name: "Wishboard",
        component: () => import("./views/WishManageView.vue"),
        meta: {
          title: "心愿便签",
          searchable: true,
          menu: {
            name: "心愿便签",
            group: "tool",
            icon: markRaw(IconSettings),
            priority: 1,
          },
        },
      },
    },
  ],
  extensionPoints: {},
});
