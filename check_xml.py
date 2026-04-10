import os
import glob
import codecs

res_dir = r"C:\Users\Bhanu Kiran\.gemini\antigravity\scratch\SmartMess\app\src\main\res"

for root, _, files in os.walk(res_dir):
    for filename in files:
        if filename.endswith(".xml"):
            path = os.path.join(root, filename)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                    if not content.startswith("<?xml"):
                        print(f"Bad XML start found in: {path}")
                        print(f"Starts with: {repr(content[:20])}")
            except Exception as e:
                # Could be a different encoding
                pass
