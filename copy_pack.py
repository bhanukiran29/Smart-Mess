import shutil
import os

src_dir = r"C:\Users\Bhanu Kiran\Downloads\smartmess_complete_pack\smartmess_complete\app\src\main"
dest_dir = r"C:\Users\Bhanu Kiran\.gemini\antigravity\scratch\SmartMess\app\src\main"

print(f"Copying files from {src_dir} to {dest_dir} ...")

try:
    shutil.copytree(src_dir, dest_dir, dirs_exist_ok=True)
    print("Files copied successfully.")
except Exception as e:
    print(f"Error copying files: {e}")
