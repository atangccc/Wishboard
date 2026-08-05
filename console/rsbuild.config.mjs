import { rsbuildConfig } from "@halo-dev/ui-plugin-bundler-kit";

const OUT_DIR_PROD = "../src/main/resources/console";
const OUT_DIR_DEV = "../build/resources/main/console";

export default rsbuildConfig({
  rsbuild: ({ envMode }) => {
    const isProduction = envMode === "production";
    return {
      output: {
        distPath: {
          root: isProduction ? OUT_DIR_PROD : OUT_DIR_DEV,
        },
      },
    };
  },
});
