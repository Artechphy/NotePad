"""Host-side client for the isolated Android public-UI test controller."""
from pathlib import Path
import datetime
import json
import os
import socket
import re
import subprocess
import time

PACKAGE = "com.example.android.notepad"

class Device:
    def __init__(self, evidence, serial="emulator-5580"):
        self.evidence = Path(evidence).resolve()
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.serial = os.environ.get("NOTEPAD_SERIAL", serial)
        self.adb = os.environ.get("NOTEPAD_ADB", "/Users/artechphy/Library/Android/sdk/platform-tools/adb")
        self.sequence = 0

    def shell(self, *args):
        result = subprocess.check_output([self.adb, "-s", self.serial, "shell", *args], text=True)
        with (self.evidence / "device-commands.jsonl").open("a") as output:
            output.write(json.dumps({"at":datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "command":list(args),"output":result},ensure_ascii=False)+"\n")
        return result

    def rpc(self, action, **args):
        request = {"action": action, **args}
        with socket.create_connection(("127.0.0.1", 18765), timeout=12) as stream:
            stream.sendall((json.dumps(request, ensure_ascii=False)+"\n").encode())
            response = json.loads(stream.makefile("r", encoding="utf-8").readline())
        self.sequence += 1
        with (self.evidence / "ui-actions.jsonl").open("a") as output:
            output.write(json.dumps({"at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "request": request, "response": response}, ensure_ascii=False)+"\n")
        if not response.get("ok"):
            raise RuntimeError(response)
        return response.get("nodes", [])

    def nodes(self): return self.rpc("dump")
    def touch(self, match, by, hold=False):
        nodes = self.nodes()
        node = next((n for n in nodes if n.get("visible") and
                     ((by=="row_text" and n.get("id")=="android:id/text1" and n.get("text")==match)
                      or (by!="row_text" and n.get(by)==match))), None)
        if node is None: raise AssertionError("Visible UI node not found: "+match)
        left,top,right,bottom = map(int,re.findall(r"-?\d+",node["bounds"]))
        x,y=str((left+right)//2),str((top+bottom)//2)
        if hold: self.shell("input","swipe",x,y,x,y,"800")
        else: self.shell("input","tap",x,y)
        time.sleep(.25)
        return self.nodes()
    def click(self, match, by="id"): return self.touch(match,by)
    def long(self, match, by="text"): return self.touch(match,by,True)
    def set(self, match, value): return self.rpc("set", match=match, value=value)
    def key(self, code):
        self.shell("input", "keyevent", str(code))
        time.sleep(.3)
        return self.nodes()

    def wait(self, predicate, label, timeout=15):
        start = time.monotonic()
        while True:
            nodes = self.nodes()
            if predicate(nodes): return nodes
            if time.monotonic()-start > timeout:
                self.capture("timeout-"+label)
                raise AssertionError("UI wait expired: "+label)
            time.sleep(.25)

    def capture(self, label):
        nodes = self.nodes()
        (self.evidence / (label+".json")).write_text(json.dumps(nodes, ensure_ascii=False, indent=2))
        with (self.evidence / (label+".png")).open("wb") as output:
            subprocess.run([self.adb, "-s", self.serial, "exec-out", "screencap", "-p"], stdout=output, check=True)
        return nodes

    def launch(self):
        return self.shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                          "-n", PACKAGE+"/.NotesList")

    def stop_app(self): return self.shell("am", "force-stop", PACKAGE)

    def record(self, case, status, detail, evidence):
        record={"case":case,"status":status,"detail":detail,"evidence":evidence,
                "at":datetime.datetime.now(datetime.timezone.utc).isoformat()}
        with (self.evidence / "results.jsonl").open("a") as output:
            output.write(json.dumps(record, ensure_ascii=False)+"\n")
        print(json.dumps(record, ensure_ascii=False), flush=True)

if __name__ == "__main__":
    import sys
    d=Device(sys.argv[1])
    if len(sys.argv)>2:
        req=json.loads(sys.argv[2]); nodes=d.rpc(**req)
    else: nodes=d.nodes()
    print(json.dumps([n for n in nodes if n.get("visible")],ensure_ascii=False,indent=2))
