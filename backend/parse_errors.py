
import re

with open('../.output.txt', 'r') as f:
    lines = f.readlines()

errors = []
current_error = ""

for line in lines:
    if line.startswith("[ERROR] /"):
        if current_error:
            errors.append(current_error)
        current_error = line.strip()
    elif line.startswith("[ERROR]  "):
        current_error += " " + line.replace("[ERROR]", "").strip()

if current_error:
    errors.append(current_error)

for err in errors:
    print(err)
