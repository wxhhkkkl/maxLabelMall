"""Extract scene assets from the approved mockups for the implemented website."""
from pathlib import Path
from PIL import Image

source = Path(__file__).resolve().parent
target = source.parents[1] / 'storefront' / 'public' / 'assets' / 'solutions'
target.mkdir(parents=True, exist_ok=True)
for path in sorted(source.glob('[0-9][0-9]-*.png')):
    image = Image.open(path)
    width, height = image.size
    # Only the photographic part of each hero, excluding navigation and body copy.
    bounds = (0.50, 0.04, 1.0, 0.192) if path.stem == '00-overview' else (0.60, 0.045, 1.0, 0.255)
    scene = image.crop(tuple(round(v * (width if i % 2 == 0 else height)) for i, v in enumerate(bounds)))
    scene.thumbnail((1000, 800), Image.Resampling.LANCZOS)
    scene.save(target / f'{path.stem}.webp', 'WEBP', quality=88)
    print(f'{path.stem}: {scene.size}')

    if path.stem == '00-overview':
        continue
    banner_bounds = (0.36, 0.037, 1.0, 0.282)
    banner = image.crop(tuple(round(v * (width if i % 2 == 0 else height)) for i, v in enumerate(banner_bounds)))
    banner.save(target / f'{path.stem}-banner.webp', 'WEBP', quality=92)

    # Photo / label illustrations from the sample sections of each approved design.
    sample_bounds = {
        '01-warehouse': [(0.066, 0.680, 0.313, 0.774), (0.382, 0.680, 0.628, 0.774), (0.703, 0.680, 0.940, 0.774)],
        '02-manufacturing': [(0.032, 0.604, 0.341, 0.778), (0.359, 0.604, 0.662, 0.778), (0.681, 0.604, 0.968, 0.778)],
        '03-apparel': [(0.036, 0.584, 0.336, 0.745), (0.355, 0.584, 0.644, 0.745), (0.669, 0.584, 0.965, 0.745)],
        '04-medical': [(0.052, 0.641, 0.325, 0.765), (0.373, 0.641, 0.648, 0.765), (0.696, 0.641, 0.967, 0.765)],
        '05-food': [(0.047, 0.642, 0.340, 0.774), (0.372, 0.642, 0.644, 0.774), (0.694, 0.642, 0.956, 0.774)],
        '06-retail': [(0.076, 0.659, 0.332, 0.773), (0.374, 0.659, 0.658, 0.773), (0.699, 0.659, 0.959, 0.773)],
        '07-crossborder': [(0.066, 0.628, 0.328, 0.754), (0.385, 0.628, 0.644, 0.754), (0.705, 0.628, 0.956, 0.754)],
        '08-bakery': [(0.041, 0.623, 0.340, 0.780), (0.358, 0.623, 0.645, 0.780), (0.667, 0.623, 0.960, 0.780)],
        '09-catering': [(0.260, 0.632, 0.463, 0.725), (0.503, 0.632, 0.700, 0.725), (0.742, 0.632, 0.944, 0.725)],
        '10-semiconductor': [(0.041, 0.663, 0.328, 0.790), (0.357, 0.663, 0.640, 0.790), (0.673, 0.663, 0.962, 0.790)],
    }
    for index, bounds in enumerate(sample_bounds[path.stem], 1):
        illustration = image.crop(tuple(round(v * (width if i % 2 == 0 else height)) for i, v in enumerate(bounds)))
        illustration.save(target / f'{path.stem}-label-{index}.webp', 'WEBP', quality=92)

    if path.stem == '01-warehouse':
        for index, (left, right) in enumerate([(0.054, 0.241), (0.293, 0.480), (0.532, 0.720), (0.772, 0.960)], 1):
            photo = image.crop((round(left * width), round(.404 * height), round(right * width), round(.498 * height)))
            photo.save(target / f'{path.stem}-step-{index}.webp', 'WEBP', quality=92)

    pain_bounds = {
        '05-food': [(0.281, 0.349, 0.463, 0.415), (0.527, 0.349, 0.727, 0.415), (0.769, 0.349, 0.960, 0.415)],
        '08-bakery': [(0.249, 0.326, 0.322, 0.435), (0.575, 0.326, 0.642, 0.435), (0.884, 0.326, 0.958, 0.435)],
    }
    for index, bounds in enumerate(pain_bounds.get(path.stem, []), 1):
        photo = image.crop(tuple(round(v * (width if i % 2 == 0 else height)) for i, v in enumerate(bounds)))
        photo.save(target / f'{path.stem}-pain-{index}.webp', 'WEBP', quality=92)
