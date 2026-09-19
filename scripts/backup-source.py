from pathlib import Path
import os,zipfile,hashlib,json
root=Path(r"E:\To-Do\pounce")
skip={"build",".gradle",".git","artifacts","releases","private"}
paths=[]
for directory,dirs,files in os.walk(root):
 dirs[:]=[d for d in dirs if d not in skip]
 for name in files:
  path=Path(directory)/name
  if name in {"local.properties","emulator.log","emulator-error.log"}:continue
  paths.append(path)
dest=root/"releases/Pounce-0.5.0-source.zip"
with zipfile.ZipFile(dest,"w",zipfile.ZIP_DEFLATED) as z:
 for path in paths:z.write(path,Path("pounce")/path.relative_to(root))
assert all("private/" not in n and not n.endswith(".jks") for n in zipfile.ZipFile(dest).namelist())
private=Path(r"E:\To-Do\Private-Recovery\Pounce-0.5.0-private-recovery.zip")
private.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(private,"w",zipfile.ZIP_DEFLATED) as z:
 for path in paths:z.write(path,Path("pounce")/path.relative_to(root))
 for path in (root/"private").iterdir():
  if path.is_file():z.write(path,Path("pounce/private")/path.name)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
apk=root/"releases/Pounce-0.5.0.apk"
receipt={"apk":apk.name,"apk_bytes":apk.stat().st_size,"apk_sha256":sha(apk),"source":dest.name,"source_sha256":sha(dest),"source_files":len(paths),"private_material_excluded_from_source":True}
(root/"releases/checksums.json").write_text(json.dumps(receipt,indent=2))
print(json.dumps(receipt))
