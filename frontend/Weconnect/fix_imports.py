import os
import glob

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    modified = False
    
    # Fix activities imports
    if "com.example.weconnect.activities.*" in content:
        content = content.replace("com.example.weconnect.activities.*", "com.example.weconnect.presentation.ui.*")
        modified = True
    
    if "com.example.weconnect.activities." in content:
        content = content.replace("com.example.weconnect.activities.", "com.example.weconnect.presentation.ui.")
        modified = True
        
    # Fix api imports
    if "com.example.weconnect.api.FirebaseManager" in content:
        content = content.replace("com.example.weconnect.api.FirebaseManager", "com.example.weconnect.data.repository.FirebaseManager")
        modified = True
        
    if modified:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Fixed {filepath}")

base_dir = "/Users/dodangnguyen/Documents/GitHub/weconnectW/frontend/Weconnect/app/src/main/java/com/example/weconnect"

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith(".java"):
            process_file(os.path.join(root, file))

print("Done fixing imports")
