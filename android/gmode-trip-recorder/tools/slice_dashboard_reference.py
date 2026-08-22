"""Create exact Android dashboard skin pieces from the supplied reference image."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageChops


DESIGN_SIZE = (1280, 592)
SLICES = {
    "reference_dashboard_top.png": (0, 0, 1280, 98),
    "reference_dashboard_middle_left.png": (0, 98, 428, 466),
    "reference_dashboard_middle_center.png": (428, 98, 852, 466),
    "reference_dashboard_middle_right.png": (852, 98, 1280, 466),
    "reference_dashboard_footer.png": (0, 466, 1280, 592),
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def create_dynamic_master(source: Path, output: Path) -> None:
    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Could not read {source}")

    # Remove only the foreground glyphs that must be redrawn from live state.
    # Colour-keying the text preserves the source texture around every glyph;
    # rectangular masks visibly flatten the leather and terrain.
    mask = np.zeros(image.shape[:2], dtype=np.uint8)
    regions = (
        ((570, 12, 710, 63), True, False),   # white clock
        ((592, 111, 687, 139), True, False), # white gauge title
        ((585, 362, 697, 407), False, True), # red gauge value
        ((540, 497, 742, 575), True, True),  # white/red footer readout
    )
    for (left, top, right, bottom), remove_white, remove_red in regions:
        roi = image[top:bottom, left:right]
        blue, green, red = cv2.split(roi)
        selected = np.zeros(roi.shape[:2], dtype=bool)
        if remove_white:
            selected |= (
                (blue > 110)
                & (green > 110)
                & (red > 110)
                & ((np.maximum.reduce((blue, green, red)).astype(np.int16) - np.minimum.reduce((blue, green, red))) < 42)
            )
        if remove_red:
            selected |= (red > 35) & (red.astype(np.int16) > green.astype(np.int16) * 3 // 2) & (red.astype(np.int16) > blue.astype(np.int16) * 3 // 2)
        mask[top:bottom, left:right][selected] = 255

    mask = cv2.dilate(mask, np.ones((3, 3), dtype=np.uint8), iterations=5)
    cleaned = cv2.inpaint(image, mask, 5, cv2.INPAINT_NS)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(output), cleaned, [cv2.IMWRITE_PNG_COMPRESSION, 9]):
        raise OSError(f"Could not write {output}")


def slice_master(master: Path, output_dir: Path) -> None:
    with Image.open(master) as image:
        image = image.convert("RGB")
        if image.size != DESIGN_SIZE:
            raise ValueError(f"Expected {DESIGN_SIZE[0]}x{DESIGN_SIZE[1]}, got {image.size[0]}x{image.size[1]}")

        output_dir.mkdir(parents=True, exist_ok=True)
        pieces: dict[str, Image.Image] = {}
        for filename, bounds in SLICES.items():
            piece = image.crop(bounds)
            piece.save(output_dir / filename, optimize=True, compress_level=9)
            pieces[filename] = piece

        reconstructed = Image.new("RGB", DESIGN_SIZE)
        reconstructed.paste(pieces["reference_dashboard_top.png"], (0, 0))
        reconstructed.paste(pieces["reference_dashboard_middle_left.png"], (0, 98))
        reconstructed.paste(pieces["reference_dashboard_middle_center.png"], (428, 98))
        reconstructed.paste(pieces["reference_dashboard_middle_right.png"], (852, 98))
        reconstructed.paste(pieces["reference_dashboard_footer.png"], (0, 466))
        if ImageChops.difference(image, reconstructed).getbbox() is not None:
            raise AssertionError("Slice reconstruction changed one or more pixels")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--cleaned-master", type=Path)
    args = parser.parse_args()

    cleaned_master = args.cleaned_master or args.output_dir / "reference_dashboard_cleaned.png"
    create_dynamic_master(args.source, cleaned_master)
    slice_master(cleaned_master, args.output_dir)
    print(f"source={args.source} sha256={sha256(args.source)}")
    print(f"cleaned_master={cleaned_master} sha256={sha256(cleaned_master)}")
    print("pixel-perfect slice reconstruction=true")


if __name__ == "__main__":
    main()
