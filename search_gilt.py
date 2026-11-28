import requests

r = requests.get("https://api.mfapi.in/mf/search", params={"q": "gilt"})
for item in r.json():
    print(item["schemeCode"], item["schemeName"])