# TensorIMS v4.0.2-ar1 (Arabic private build)

Fork of [Pixel-Tailor-CN/TensorIMS](https://github.com/Pixel-Tailor-CN/TensorIMS) at v4.0.2 (commit 0b114fe), licensed under Apache License 2.0 (see LICENSE). Modified for personal use.

Changes vs upstream:
- Added a full Arabic translation (values-ar) covering every string resource.
- Extracted 61 hardcoded Chinese backend/status messages into string resources with English and Arabic translations (new BackendStrings holder initialized by the Application).
- Removed the Chinese (zh-rCN) locale; locales_config.xml now lists en + ar only.
- Removed the Settings > About section (version row, PixelTailorCN website link, Telegram link) and the "Maintained by Mystery0" home-screen subtitle, with their dead strings.
- Release builds are unsigned (upstream intentionally aborts release packaging without signing credentials; that guard was removed).
- Version label set to 4.0.2-ar1.
- No functional or default-behavior changes.
