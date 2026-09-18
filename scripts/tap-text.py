import re
import subprocess
import sys
import xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter('node'):
    if node.attrib.get('text') == sys.argv[2]:
        left, top, right, bottom = map(int, re.findall(r'\d+', node.attrib['bounds']))
        subprocess.run(['adb', 'shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2)], check=True)
        break
else:
    raise SystemExit('Requested control was not present: ' + sys.argv[2])
