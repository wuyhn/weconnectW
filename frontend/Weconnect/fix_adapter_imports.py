import os

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    modified = False
    
    # Fix package declarations in the adapter files
    if "package com.example.weconnect.adapters;" in content:
        content = content.replace("package com.example.weconnect.adapters;", "package com.example.weconnect.presentation.adapter;")
        modified = True

    # Fix imports of adapters anywhere in the code
    if "import com.example.weconnect.adapters." in content:
        content = content.replace("import com.example.weconnect.adapters.", "import com.example.weconnect.presentation.adapter.")
        modified = True
        
    if modified:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Fixed adapter imports in {filepath}")

base_dir = "/Users/dodangnguyen/Documents/GitHub/weconnectW/frontend/Weconnect/app/src/main/java/com/example/weconnect"

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith(".java"):
            process_file(os.path.join(root, file))

print("Done fixing adapter imports")
