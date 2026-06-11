# RtkCollector Logo Branding Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate reproducible Android logo assets from `logos/rtkcollector_logo.png`, wire them into the app launcher metadata, and add compact in-app brand usage without changing recording behaviour.

**Architecture:** A small Python/Pillow generator creates all derived raster assets from the checked-in source logo. Android resources remain conventional: launcher mipmaps, adaptive icon XML, colour resources, and drawable-nodpi branding assets. Compose UI only references the rectangular badge in low-risk branding contexts; live monitoring remains data-first.

**Tech Stack:** Android resources, Kotlin/Compose, Python 3 with Pillow for deterministic asset generation, Gradle verification.

---

## File Structure

- Create: `tools/generate_brand_assets.py`
  - Reads `logos/rtkcollector_logo.png`.
  - Crops the left satellite/pin symbol into square launcher assets.
  - Creates a compact rectangular badge asset from the full wordmark.
  - Writes Android resources under `app/src/main/res/`.
- Create: `tools/test_generate_brand_assets.py`
  - Unit tests crop-size and output path behaviour using a synthetic test image.
- Create/generated: `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- Create/generated: `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- Create/generated: `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- Create/generated: `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- Create/generated: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- Create/generated: `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- Create/generated: `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- Create/generated: `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- Create/generated: `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- Create/generated: `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`
- Create/generated: `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png`
- Create/generated: `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png`
- Create/generated: `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png`
- Create/generated: `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png`
- Create/generated: `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create/modify: `app/src/main/res/values/colors.xml`
- Create/generated: `app/src/main/res/drawable-nodpi/rtkcollector_wordmark.png`
- Create/generated: `app/src/main/res/drawable-nodpi/rtkcollector_badge_rect.png`
- Modify: `app/src/main/AndroidManifest.xml`
  - Add `android:icon` and `android:roundIcon`.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Add a compact branding image only where existing header space allows; do not reduce live telemetry density.
- Modify: `README.md`
  - Add a short note that app branding assets are generated from `logos/`.

---

## Task 1: Add A Deterministic Asset Generator

**Files:**
- Create: `tools/generate_brand_assets.py`
- Create: `tools/test_generate_brand_assets.py`

- [ ] **Step 1: Write the failing generator tests**

Create `tools/test_generate_brand_assets.py`:

```python
from pathlib import Path

from PIL import Image

from generate_brand_assets import crop_symbol, generate_assets


def test_crop_symbol_returns_square_symbol_region(tmp_path):
    source = tmp_path / "logo.png"
    image = Image.new("RGB", (1354, 527), "white")
    image.paste("navy", (80, 40, 390, 470))
    image.save(source)

    cropped = crop_symbol(Image.open(source))

    assert cropped.size[0] == cropped.size[1]
    assert cropped.size[0] >= 430


def test_generate_assets_writes_android_outputs(tmp_path):
    source = tmp_path / "logo.png"
    image = Image.new("RGB", (1354, 527), "white")
    image.paste("navy", (80, 40, 390, 470))
    image.save(source)

    res_dir = tmp_path / "app" / "src" / "main" / "res"
    generate_assets(source, res_dir)

    assert (res_dir / "mipmap-mdpi" / "ic_launcher.png").is_file()
    assert (res_dir / "mipmap-xxxhdpi" / "ic_launcher_foreground.png").is_file()
    assert (res_dir / "drawable-nodpi" / "rtkcollector_wordmark.png").is_file()
    assert (res_dir / "drawable-nodpi" / "rtkcollector_badge_rect.png").is_file()
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
python3 -m pytest tools/test_generate_brand_assets.py -q
```

Expected: fail with `ModuleNotFoundError: No module named 'generate_brand_assets'`.

- [ ] **Step 3: Implement the generator**

Create `tools/generate_brand_assets.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageOps


DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def crop_symbol(image: Image.Image) -> Image.Image:
    """Crop the left satellite/pin symbol from the wide RtkCollector logo."""
    width, height = image.size
    left = 0
    right = int(width * 0.34)
    top = 0
    bottom = height
    symbol = image.crop((left, top, right, bottom)).convert("RGBA")
    side = max(symbol.width, symbol.height)
    square = Image.new("RGBA", (side, side), (255, 255, 255, 0))
    square.alpha_composite(symbol, ((side - symbol.width) // 2, (side - symbol.height) // 2))
    return square


def fit_canvas(image: Image.Image, size: tuple[int, int], padding_ratio: float = 0.08) -> Image.Image:
    """Fit an image onto a transparent canvas while preserving aspect ratio."""
    canvas = Image.new("RGBA", size, (255, 255, 255, 0))
    max_size = (
        int(size[0] * (1.0 - padding_ratio * 2.0)),
        int(size[1] * (1.0 - padding_ratio * 2.0)),
    )
    fitted = ImageOps.contain(image.convert("RGBA"), max_size, Image.Resampling.LANCZOS)
    canvas.alpha_composite(fitted, ((size[0] - fitted.width) // 2, (size[1] - fitted.height) // 2))
    return canvas


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def generate_assets(source_logo: Path, res_dir: Path) -> None:
    logo = Image.open(source_logo).convert("RGBA")
    symbol = crop_symbol(logo)

    for density, size in DENSITIES.items():
        icon = fit_canvas(symbol, (size, size), padding_ratio=0.05)
        save_png(icon, res_dir / f"mipmap-{density}" / "ic_launcher.png")
        save_png(icon, res_dir / f"mipmap-{density}" / "ic_launcher_round.png")

    for density, size in FOREGROUND_DENSITIES.items():
        foreground = fit_canvas(symbol, (size, size), padding_ratio=0.16)
        save_png(foreground, res_dir / f"mipmap-{density}" / "ic_launcher_foreground.png")

    save_png(fit_canvas(logo, (1080, 420), padding_ratio=0.02), res_dir / "drawable-nodpi" / "rtkcollector_wordmark.png")
    save_png(fit_canvas(logo, (720, 280), padding_ratio=0.04), res_dir / "drawable-nodpi" / "rtkcollector_badge_rect.png")


def main() -> None:
    repo_root = Path(__file__).resolve().parents[1]
    generate_assets(
        source_logo=repo_root / "logos" / "rtkcollector_logo.png",
        res_dir=repo_root / "app" / "src" / "main" / "res",
    )


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run tests and verify they pass**

Run:

```bash
python3 -m pytest tools/test_generate_brand_assets.py -q
```

Expected: `2 passed`.

- [ ] **Step 5: Generate brand assets**

Run:

```bash
python3 tools/generate_brand_assets.py
```

Expected: PNG files are written under `app/src/main/res/mipmap-*` and `app/src/main/res/drawable-nodpi/`.

- [ ] **Step 6: Commit**

```bash
git add tools/generate_brand_assets.py tools/test_generate_brand_assets.py app/src/main/res/mipmap-* app/src/main/res/drawable-nodpi
git commit -m "Generate RtkCollector Android brand assets"
```

---

## Task 2: Wire Android Launcher Icon Resources

**Files:**
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create/modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add adaptive icon XML**

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 2: Add launcher background colour**

If `app/src/main/res/values/colors.xml` does not exist, create it:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFFFFF</color>
</resources>
```

If the file exists, add only:

```xml
<color name="ic_launcher_background">#FFFFFF</color>
```

- [ ] **Step 3: Update manifest icon metadata**

Modify the `<application>` element in `app/src/main/AndroidManifest.xml` so it contains:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

The resulting element should keep existing attributes:

```xml
<application
    android:allowBackup="false"
    android:icon="@mipmap/ic_launcher"
    android:label="RtkCollector"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/AppTheme">
```

- [ ] **Step 4: Compile Android resources where feasible**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected in this Termux environment: Kotlin compilation should pass if Android resource processing is already up to date; if Gradle runs `:app:processDebugResources`, it may fail on the known local `aapt2` native binary limitation. If it fails at `aapt2`, report that blocker and run the receiver/app-free tests from Task 1 again.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/mipmap-anydpi-v26 app/src/main/res/values/colors.xml
git commit -m "Wire RtkCollector launcher icons"
```

---

## Task 3: Add Restrained In-App Branding

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Inspect the existing header**

Run:

```bash
rg -n "RtkCollector|TopAppBar|Dashboard|Header|Menu" app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
```

Expected: find the existing dashboard title/header composable.

- [ ] **Step 2: Add a compact image resource in the header**

In the header composable, add a compact image using the generated rectangular badge only if the existing layout has room. The code should be equivalent to:

```kotlin
Image(
    painter = painterResource(id = R.drawable.rtkcollector_badge_rect),
    contentDescription = "RtkCollector",
    modifier = Modifier
        .height(28.dp)
        .widthIn(max = 150.dp),
    contentScale = ContentScale.Fit,
)
```

Add the required imports if missing:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.rtkcollector.app.R
```

If the header is too dense, use the square symbol in a `32.dp` box instead:

```kotlin
Image(
    painter = painterResource(id = R.mipmap.ic_launcher),
    contentDescription = "RtkCollector",
    modifier = Modifier.size(32.dp),
)
```

Do not place branding inside telemetry cards or recording status values.

- [ ] **Step 3: Verify Compose compilation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: pass unless the local environment triggers the known Android `aapt2` resource-processing limitation first. If `aapt2` fails, report the exact task and message.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Add restrained RtkCollector dashboard branding"
```

---

## Task 4: Document Asset Regeneration

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add a short branding asset note**

Add this section near development/build notes:

```markdown
## Branding Assets

Source logo files live in `logos/`. Android launcher and compact badge assets
are generated from `logos/rtkcollector_logo.png` with:

```bash
python3 tools/generate_brand_assets.py
```

The generated launcher assets are checked in so Android Studio and CI builds do
not depend on local image tooling.
```
```

- [ ] **Step 2: Check markdown formatting**

Run:

```bash
git diff -- README.md
```

Expected: the fenced code block is closed correctly and the note is concise.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Document RtkCollector branding assets"
```

---

## Task 5: Final Verification And Push

**Files:**
- Review all changed files from Tasks 1-4.

- [ ] **Step 1: Verify no local visual companion or samples are staged**

Run:

```bash
git status --short
```

Expected: `.superpowers/`, `.codex-tmp/`, and `samples/` may remain untracked, but they must not be staged. The `logos/` source files should be staged only if the user wants the source logos committed.

- [ ] **Step 2: Run focused tests**

Run:

```bash
python3 -m pytest tools/test_generate_brand_assets.py -q
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980LiveParsersTest
```

Expected: both pass.

- [ ] **Step 3: Run Android build check where feasible**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: pass if the local Android resource tooling is usable. If it fails because Termux cannot execute the Android `aapt2` binary, record the exact failure and state that Windows Android Studio/CI should run the full APK packaging check.

- [ ] **Step 4: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Push**

Run:

```bash
git push origin main
```

Expected: `main -> main`.

---

## Self-Review

- Spec coverage: the plan generates square launcher icons, rectangular badge assets, full wordmark assets, manifest icon metadata, and minimal in-app usage.
- Scope: no recording, USB, NTRIP, session format or telemetry behaviour changes are included.
- Asset reproducibility: the generator and source logo path are explicit.
- Validation: tests cover generation logic; Gradle and Android verification commands are listed with the known Termux limitation.
- Placeholder scan: no unresolved placeholder markers or open design choices remain.
