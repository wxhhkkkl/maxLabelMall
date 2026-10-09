"""Encode standalone photographic masters without upscaling or mockup cropping."""
from pathlib import Path
from PIL import Image

source = Path(__file__).resolve().parent
target = source.parents[1] / 'storefront' / 'public' / 'assets' / 'solutions'
for name in ['00-overview-v2', '08-bakery-v2', '08-bakery-label-1-v2', '08-bakery-label-2-v2', '08-bakery-label-3-v2']:
    master = source / f'{name}.png'
    if not master.exists():
        continue
    image = Image.open(master).convert('RGB')
    destination = target / f'{name}.webp'
    image.save(destination, 'WEBP', quality=93, method=6)
    print(name, image.size, destination.stat().st_size)
