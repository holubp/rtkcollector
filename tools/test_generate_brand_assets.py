from pathlib import Path

from PIL import Image

from generate_brand_assets import crop_symbol, generate_assets


EXPECTED_OUTPUTS = {
    "mipmap-mdpi/ic_launcher.png": (48, 48),
    "mipmap-hdpi/ic_launcher.png": (72, 72),
    "mipmap-xhdpi/ic_launcher.png": (96, 96),
    "mipmap-xxhdpi/ic_launcher.png": (144, 144),
    "mipmap-xxxhdpi/ic_launcher.png": (192, 192),
    "mipmap-mdpi/ic_launcher_round.png": (48, 48),
    "mipmap-hdpi/ic_launcher_round.png": (72, 72),
    "mipmap-xhdpi/ic_launcher_round.png": (96, 96),
    "mipmap-xxhdpi/ic_launcher_round.png": (144, 144),
    "mipmap-xxxhdpi/ic_launcher_round.png": (192, 192),
    "mipmap-mdpi/ic_launcher_foreground.png": (108, 108),
    "mipmap-hdpi/ic_launcher_foreground.png": (162, 162),
    "mipmap-xhdpi/ic_launcher_foreground.png": (216, 216),
    "mipmap-xxhdpi/ic_launcher_foreground.png": (324, 324),
    "mipmap-xxxhdpi/ic_launcher_foreground.png": (432, 432),
    "drawable-nodpi/rtkcollector_wordmark.png": (1080, 420),
    "drawable-nodpi/rtkcollector_badge_rect.png": (720, 280),
}


def test_crop_symbol_returns_square_symbol_region(tmp_path):
    source = tmp_path / "logo.png"
    image = Image.new("RGB", (1354, 527), "white")
    image.paste("navy", (80, 40, 390, 470))
    image.save(source)

    cropped = crop_symbol(Image.open(source))

    assert cropped.size == (527, 527)
    assert cropped.getbbox() == (54, 0, 473, 527)


def test_crop_symbol_excludes_wordmark_region(tmp_path):
    source = tmp_path / "logo.png"
    image = Image.new("RGB", (1354, 527), "white")
    image.paste("navy", (80, 40, 390, 470))
    image.paste("red", (430, 120, 900, 360))
    image.save(source)

    cropped = crop_symbol(Image.open(source))

    pixels = cropped.tobytes()
    assert not any(
        pixels[index] > 200 and pixels[index + 1] < 80 and pixels[index + 2] < 80 and pixels[index + 3] > 0
        for index in range(0, len(pixels), 4)
    )


def test_generate_assets_writes_android_outputs(tmp_path):
    source = tmp_path / "logo.png"
    image = Image.new("RGB", (1354, 527), "white")
    image.paste("navy", (80, 40, 390, 470))
    image.save(source)

    res_dir = tmp_path / "app" / "src" / "main" / "res"
    generate_assets(source, res_dir)

    generated = {
        path.relative_to(res_dir).as_posix(): Image.open(path).size
        for path in res_dir.rglob("*.png")
    }

    assert generated == EXPECTED_OUTPUTS
