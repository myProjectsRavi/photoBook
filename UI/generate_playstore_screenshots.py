import os
from PIL import Image, ImageDraw, ImageFilter

def add_corners(im, rad):
    circle = Image.new('L', (rad * 2, rad * 2), 0)
    draw = ImageDraw.Draw(circle)
    draw.ellipse((0, 0, rad * 2 - 1, rad * 2 - 1), fill=255)
    alpha = Image.new('L', im.size, 255)
    w, h = im.size
    alpha.paste(circle.crop((0, 0, rad, rad)), (0, 0))
    alpha.paste(circle.crop((0, rad, rad, rad * 2)), (0, h - rad))
    alpha.paste(circle.crop((rad, 0, rad * 2, rad)), (w - rad, 0))
    alpha.paste(circle.crop((rad, rad, rad * 2, rad * 2)), (w - rad, h - rad))
    im.putalpha(alpha)
    return im

def drop_shadow(image, offset=(0, 20), blur=30, color=(0, 0, 0, 100)):
    shadow = Image.new('RGBA', image.size, color)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    
    # Create an expanded image to hold the shadow without clipping
    new_size = (image.width + offset[0] + blur*2, image.height + offset[1] + blur*2)
    final_img = Image.new('RGBA', new_size, (0,0,0,0))
    
    # Paste shadow
    shadow_x = blur + offset[0]
    shadow_y = blur + offset[1]
    final_img.paste(shadow, (shadow_x, shadow_y))
    
    # Paste original image
    final_img.paste(image, (blur, blur), image)
    return final_img

def create_gradient_bg(width, height, start_color, end_color):
    base = Image.new('RGB', (width, height), start_color)
    top = Image.new('RGB', (width, height), end_color)
    mask = Image.new('L', (width, height))
    mask_data = []
    for y in range(height):
        for x in range(width):
            mask_data.append(int(255 * (y / height)))
    mask.putdata(mask_data)
    base.paste(top, (0, 0), mask)
    return base

# We are running this script inside the UI directory
input_dir = "New UI"
output_dir = "PlayStore_Screenshots"

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

# Simple modern gradient: Soft Blue to Soft Purple
bg_start = (235, 244, 255) # Light blue-ish
bg_end = (243, 232, 255)   # Light purple-ish

canvas_w, canvas_h = 1080, 1920

images = [f for f in os.listdir(input_dir) if "refined_glass" in f and f.endswith(".png")]

for idx, filename in enumerate(sorted(images)):
    try:
        img_path = os.path.join(input_dir, filename)
        screenshot = Image.open(img_path).convert("RGBA")
        
        # Determine scaling to fit nicely inside canvas
        target_w = 900
        aspect_ratio = screenshot.height / screenshot.width
        target_h = int(target_w * aspect_ratio)
        
        if target_h > 1500: # if too tall, constrain by height instead
            target_h = 1500
            target_w = int(target_h / aspect_ratio)
            
        screenshot = screenshot.resize((target_w, target_h), Image.LANCZOS)
        
        # Add rounded corners
        screenshot = add_corners(screenshot, 60)
        
        # Add shadow
        screenshot_with_shadow = drop_shadow(screenshot)
        
        # Create Background
        canvas = create_gradient_bg(canvas_w, canvas_h, bg_start, bg_end)
        
        # Center the image vertically and horizontally
        paste_x = (canvas_w - screenshot_with_shadow.width) // 2
        paste_y = (canvas_h - screenshot_with_shadow.height) // 2
        
        canvas.paste(screenshot_with_shadow, (paste_x, paste_y), screenshot_with_shadow)
        
        out_filename = f"playstore_{idx+1:02d}.png"
        canvas.save(os.path.join(output_dir, out_filename), "PNG")
        print(f"Processed: {out_filename} from {filename}")
    except Exception as e:
        print(f"Failed to process {filename}: {e}")

print("Done generating Play Store screenshots!")