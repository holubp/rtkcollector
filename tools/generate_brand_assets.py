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
    right = int(width * 0.31)
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

    save_png(
        fit_canvas(logo, (1080, 420), padding_ratio=0.02),
        res_dir / "drawable-nodpi" / "rtkcollector_wordmark.png",
    )
    save_png(
        fit_canvas(logo, (720, 280), padding_ratio=0.04),
        res_dir / "drawable-nodpi" / "rtkcollector_badge_rect.png",
    )


def main() -> None:
    repo_root = Path(__file__).resolve().parents[1]
    generate_assets(
        source_logo=repo_root / "logos" / "rtkcollector_logo.png",
        res_dir=repo_root / "app" / "src" / "main" / "res",
    )


if __name__ == "__main__":
    main()
