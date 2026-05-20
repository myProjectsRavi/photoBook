import os
import glob
from PIL import Image

input_dir = "New UI"

images = [f for f in glob.glob(os.path.join(input_dir, "refined_glass*.png"))]

for img_path in images:
    try:
        img = Image.open(img_path).convert("RGBA")
        w, h = img.size
        
        # We will scan from y=1800 to h
        # For each row, we take the pixel at x=550 and extend it to the right (x=550 to w)
        # BUT only if we detect the overlay on this row.
        # How to detect overlay? If any pixel from x=580 to w differs from x=550 by more than a threshold.
        
        pixels = img.load()
        for y in range(1800, h):
            ref_color = pixels[550, y]
            
            # Check if there's an overlay on this row
            has_overlay = False
            for x in range(580, w):
                p = pixels[x, y]
                if abs(p[0] - ref_color[0]) > 5 or abs(p[1] - ref_color[1]) > 5 or abs(p[2] - ref_color[2]) > 5:
                    has_overlay = True
                    break
            
            if has_overlay:
                # Overwrite the right side with the reference color to erase the overlay
                for x in range(551, w):
                    pixels[x, y] = ref_color
                    
        # Save the cleaned image over the original
        img.save(img_path, "PNG")
        print(f"Cleaned overlay from {img_path}")
    except Exception as e:
        print(f"Failed to clean {img_path}: {e}")

print("Done cleaning all source images.")
