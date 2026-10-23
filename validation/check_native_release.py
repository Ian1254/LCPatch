"""Read-only smoke check for the upstream native-language release used during validation."""
import hashlib
import json
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    files = [entry for entry in archive.infolist() if not entry.is_dir()]
    prefix = 'Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/jp/'
    assert files and all(entry.filename.startswith(prefix) for entry in files)
    for entry in files:
        assert entry.filename.rsplit('/', 1)[-1].startswith('JP_')
        text = archive.read(entry).decode('utf-8-sig')
        data = json.loads(text)
        assert not any(0xE000 <= ord(char) <= 0xF8FF for char in json.dumps(data, ensure_ascii=False))
    print(json.dumps({'json_files': len(files), 'native_path': prefix, 'pua': 0,
        'sha256': hashlib.sha256(open(sys.argv[1], 'rb').read()).hexdigest()}, ensure_ascii=False))
