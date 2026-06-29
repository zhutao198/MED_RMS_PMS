#!/usr/bin/env python3
"""Create 8117 project requirements via REST API"""
import json
import urllib.request

BASE_URL = "http://localhost:8080/api"

def post(url, data):
    req = urllib.request.Request(
        BASE_URL + url,
        data=json.dumps(data).encode(),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())

def create_requirement(data):
    result = post("/requirements", data)
    if result.get("code") == 200:
        return result["data"]
    print(f"Failed: {result}")
    return None

# Create URS requirements
urs_list = []
for i, (title, desc) in enumerate([
    ("ECG Signal Acquisition", "System shall acquire 12-lead ECG within 30 seconds"),
    ("AI Diagnostic Assistance", "AI algorithm with 39 detection capabilities"),
    ("Network Connectivity", "4G+WiFi dual-mode, auto upload >=99.5%"),
    ("Critical Value Alert", "Alert within 3 seconds, 3-level alerts"),
    ("Platform Integration", "Integrate with Zhongqi ECG platform"),
], 1):
    req = create_requirement({
        "title": title,
        "description": desc,
        "requirementType": "URS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH" if i <= 4 else "MEDIUM",
        "safetyClass": "B"
    })
    if req:
        urs_list.append(req)
        print(f"Created URS: {req['requirementNo']} (id={req['id']})")

print(f"\n=== Created {len(urs_list)} URS ===")

# Create PRS for each URS
prs_list = []
prs_map = {}
for urs in urs_list:
    prs = create_requirement({
        "title": f"PRS for {urs['title']}",
        "description": f"Product requirement for {urs['requirementNo']}",
        "requirementType": "PRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    })
    if prs:
        prs_list.append(prs)
        prs_map[urs['id']] = prs
        print(f"Created PRS: {prs['requirementNo']} (id={prs['id']})")

# Create SRS for each PRS
srs_list = []
srs_map = {}
for prs in prs_list:
    srs = create_requirement({
        "title": f"SRS for {prs['title']}",
        "description": f"System requirement for {prs['requirementNo']}",
        "requirementType": "SRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    })
    if srs:
        srs_list.append(srs)
        srs_map[prs['id']] = srs
        print(f"Created SRS: {srs['requirementNo']} (id={srs['id']})")

# Create DRS for each SRS
drs_list = []
for srs in srs_list:
    drs = create_requirement({
        "title": f"DRS for {srs['title']}",
        "description": f"Design requirement for {srs['requirementNo']}",
        "requirementType": "DRS",
        "projectId": 8,
        "status": "Approved",
        "priority": "MUST",
        "riskLevel": "HIGH",
        "safetyClass": "B"
    })
    if drs:
        drs_list.append(drs)
        print(f"Created DRS: {drs['requirementNo']} (id={drs['id']})")

print(f"\n=== Created {len(drs_list)} DRS ===")
print("Done!")