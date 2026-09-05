from pathlib import Path

path = Path('kspmadcow/KspMadCowScript.java')
text = path.read_text(encoding='utf-8')
old = '''                // Ferox is interaction-only: never call the generic KspVerifiedBank.openBank()\n                // here because that helper is allowed to select another bank target or\n                // start walker movement. Target the known Ferox bank chest (26711)\n                // directly, turn the camera to it when needed, then click its real bank\n                // action. The game handles the short local approach after the click.\n'''
new = '''                // Ferox is interaction-only: bypass the generic verified-bank helper here.\n                // Target the known Ferox bank chest (26711) directly, turn the camera\n                // when needed, then click its real Bank action.\n'''
if old not in text:
    raise RuntimeError('Expected Ferox bank comment not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
Path('.github/workflows/clean-verified-bank-comment.yml').unlink(missing_ok=True)
Path('.github/scripts/clean_verified_bank_comment.py').unlink(missing_ok=True)
