import preset from "@tuition/config/eslint-preset.mjs";

export default [...preset, { ignores: ["dist/**"] }];
