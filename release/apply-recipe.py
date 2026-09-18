"""Reconstruct the exact locally signed public APK. No private key is present.
The recipe copies bytes from a hash-pinned CI build and contains only public
ZIP metadata and APK signature bytes. Verify the final APK before installing.
"""
import base64
import hashlib
import json
import lzma
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_bytes()
chunks = sorted(pathlib.Path(sys.argv[2]).glob('*.txt'))
assert len(chunks) == 8
text = ''.join(p.read_text().strip() for p in chunks)
recipe = json.loads(lzma.decompress(base64.b64decode(text, validate=True)))
assert hashlib.sha256(source).hexdigest() == recipe['src']
assert recipe['sha'] == '7f0672ef7111624724656f9e10bac595c26e942d870527271a277e6203f9a189'
assert recipe['size'] == 6557951
out = bytearray()
for part in recipe['parts']:
    if isinstance(part, list):
        assert len(part) == 2 and all(isinstance(x, int) for x in part)
        offset, length = part
        assert offset >= 0 and length >= 0 and offset + length <= len(source)
        out.extend(source[offset:offset+length])
    else:
        assert isinstance(part, str)
        out.extend(base64.b64decode(part, validate=True))
    assert len(out) <= recipe['size']
assert len(out) == recipe['size']
assert hashlib.sha256(out).hexdigest() == recipe['sha']
path = pathlib.Path(sys.argv[3]); path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(out)
print('Exact signed APK reconstructed and SHA-256 verified:', recipe['sha'])
