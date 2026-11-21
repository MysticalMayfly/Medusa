#!/usr/bin/env python3
"""
Medusa publish guard. Run on every APK before it goes to the `updates` branch.

  python3 scripts/check_apk.py dist/medusa-<versionName>-b<versionCode>.apk            # checks + compares with GitHub
  python3 scripts/check_apk.py dist/medusa-<versionName>-b<versionCode>.apk --offline  # skip the GitHub comparison

Exit code 0 = safe to publish. Anything else = DO NOT publish.
Pure Python 3, no dependencies (works on the Mac and in a Linux sandbox).
"""
import hashlib, json, struct, sys, urllib.request, zipfile

PACKAGE = "com.priyabrat.medusa"
# SHA-256 of the release.jks signing certificate (alias "medusa"). Never changes.
CERT_SHA256 = "1e11bdb1a4638bfa287145a86e83fe1c3312e41f6fff7e6fc717a2591e31a2ec"
LATEST_URL = "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json"

ATTR_IDS = {0x0101021B: "versionCode", 0x0101021C: "versionName",
            0x01010272: "testOnly", 0x0101000F: "debuggable"}


def parse_manifest(data):
    """Minimal binary-XML reader: returns attrs of <manifest> and <application>."""
    pos = struct.unpack_from("<H", data, 2)[0]  # header size of XML chunk
    strings, resmap, out = [], [], {}
    while pos < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if ctype == 0x0001:  # string pool
            count, _, flags, sstart, _ = struct.unpack_from("<IIIII", data, pos + 8)
            utf8 = flags & 0x100
            offs = struct.unpack_from("<%dI" % count, data, pos + hsize)
            for o in offs:
                p = pos + sstart + o
                if utf8:
                    n = data[p]; p += 2 if n & 0x80 else 1
                    n = data[p]; p += 1
                    if n & 0x80: n = ((n & 0x7F) << 8) | data[p]; p += 1
                    strings.append(data[p:p + n].decode("utf-8", "replace"))
                else:
                    n = struct.unpack_from("<H", data, p)[0]; p += 2
                    if n & 0x8000: n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]; p += 2
                    strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
        elif ctype == 0x0180:  # resource id map
            resmap = list(struct.unpack_from("<%dI" % ((csize - hsize) // 4), data, pos + hsize))
        elif ctype == 0x0102:  # start element
            name_i = struct.unpack_from("<I", data, pos + 20)[0]
            astart, asize, acount = struct.unpack_from("<HHH", data, pos + 24)
            tag = strings[name_i]
            if tag in ("manifest", "application"):
                for i in range(acount):
                    a = pos + 16 + astart + i * asize
                    _, an, raw, _, _, dtype, dval = struct.unpack_from("<IIIHBBI", data, a)
                    key = ATTR_IDS.get(resmap[an] if an < len(resmap) else -1) or strings[an]
                    if dtype == 0x03: val = strings[dval]            # string
                    elif dtype == 0x12: val = dval != 0              # boolean
                    elif dtype in (0x10, 0x11): val = dval           # int
                    else: val = strings[raw] if raw != 0xFFFFFFFF else dval
                    out[tag + "." + key] = val
        pos += csize
    return out


def signer_certs(apk_bytes):
    """SHA-256 of signer certificates from the APK Signature Scheme v3/v2 block."""
    eocd = apk_bytes.rfind(b"PK\x05\x06")
    cd_off = struct.unpack_from("<I", apk_bytes, eocd + 16)[0]
    if apk_bytes[cd_off - 16:cd_off] != b"APK Sig Block 42":
        return []
    size = struct.unpack_from("<Q", apk_bytes, cd_off - 24)[0]
    p, end, blocks = cd_off - size - 8 + 8, cd_off - 24, {}
    while p < end:
        ln, bid = struct.unpack_from("<QI", apk_bytes, p)
        blocks[bid] = apk_bytes[p + 12:p + 8 + ln]
        p += 8 + ln
    blk = blocks.get(0xF05368C0) or blocks.get(0x7109871A)  # v3, then v2
    if blk is None:
        return []
    def lp(b, o):  # length-prefixed slice
        n = struct.unpack_from("<I", b, o)[0]
        return b[o + 4:o + 4 + n], o + 4 + n
    certs, signers = [], lp(blk, 0)[0]
    o = 0
    while o < len(signers):
        signer, o = lp(signers, o)
        signed_data = lp(signer, 0)[0]
        _, so = lp(signed_data, 0)           # digests
        cert_seq, _ = lp(signed_data, so)    # certificates
        co = 0
        while co < len(cert_seq):
            c, co = lp(cert_seq, co)
            certs.append(hashlib.sha256(c).hexdigest())
    return certs


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    path, offline = sys.argv[1], "--offline" in sys.argv
    raw = open(path, "rb").read()
    m = parse_manifest(zipfile.ZipFile(path).read("AndroidManifest.xml"))
    certs = signer_certs(raw)
    info = {
        "package": m.get("manifest.package"),
        "versionCode": m.get("manifest.versionCode"),
        "versionName": m.get("manifest.versionName"),
        "testOnly": bool(m.get("application.testOnly", False)),
        "debuggable": bool(m.get("application.debuggable", False)),
        "certSha256": certs,
        "fileSha256": hashlib.sha256(raw).hexdigest(),
        "bytes": len(raw),
    }
    print(json.dumps(info, indent=2))
    errors = []
    if info["package"] != PACKAGE: errors.append("wrong package: %s" % info["package"])
    if info["testOnly"]: errors.append("testOnly=true (Android Studio 'Run' output). Use the Assemble button or assembleRelease.")
    if certs != [CERT_SHA256]: errors.append("signed with the WRONG key %s (keystore.properties missing?)" % certs)
    if not isinstance(info["versionCode"], int): errors.append("versionCode not found")
    if not offline:
        try:
            req = urllib.request.Request(LATEST_URL, headers={"User-Agent": "medusa-check"})
            live = json.load(urllib.request.urlopen(req, timeout=15))
            print("published on GitHub: versionCode %s (%s)" % (live["versionCode"], live["versionName"]))
            if isinstance(info["versionCode"], int) and info["versionCode"] <= live["versionCode"]:
                errors.append("versionCode %s is not higher than published %s" % (info["versionCode"], live["versionCode"]))
        except Exception as e:
            errors.append("could not read published latest.json (%s); rerun with --offline only if you know the live versionCode" % e)
    if errors:
        print("\nFAIL - DO NOT PUBLISH:"); [print("  - " + e) for e in errors]; sys.exit(1)
    print("\nPASS - safe to publish")


if __name__ == "__main__":
    main()
