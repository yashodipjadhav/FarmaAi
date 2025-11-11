import os
import random
from pathlib import Path
from shutil import copy2

random.seed(42)
src_root = Path("banana_dataset")
out_root = Path("banana_data_split")

for split in ("train", "val", "test"):
    (out_root / split).mkdir(parents=True, exist_ok=True)

for cls in sorted(p.name for p in src_root.iterdir() if p.is_dir()):
    src_cls = src_root / cls
    imgs = list(src_cls.glob("*.*"))
    random.shuffle(imgs)
    n = len(imgs)
    n_train = int(0.8 * n)
    n_val = int(0.1 * n)
    train_imgs = imgs[:n_train]
    val_imgs = imgs[n_train:n_train+n_val]
    test_imgs = imgs[n_train+n_val:]

    for split, arr in [("train", train_imgs), ("val", val_imgs), ("test", test_imgs)]:
        target_dir = out_root / split / cls
        target_dir.mkdir(parents=True, exist_ok=True)
        for p in arr:
            copy2(p, target_dir / p.name)

print("✅ Train/Val/Test split created in banana_data_split/")
