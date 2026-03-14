import sys, json

d = json.load(sys.stdin)
print("Total:", d.get("total", "?"))
for e in d.get("entry", []):
    r = e["resource"]
    code = r.get("code", {})
    text = code.get("text", "N/A")
    codings = code.get("coding", [])
    subj = r.get("subject", {}).get("reference", "?")
    if codings:
        for c in codings:
            s = c.get("system", "?")
            co = c.get("code", "?")
            disp = c.get("display", "")
            print("  %s | %s|%s (%s) | %s" % (text, s, co, disp, subj))
    else:
        print("  %s | no-coding | %s" % (text, subj))
