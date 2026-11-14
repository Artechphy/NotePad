"""Build a separate, temporary UI controller APK with the installed Android SDK."""
from pathlib import Path
import subprocess
import os
import shutil

source = Path(__file__).resolve().parent
output = Path(os.environ.get("NOTEPAD_UI_BUILD", "/private/tmp/notepad-device-test/bridge"))
sdk = Path(os.environ.get("ANDROID_HOME", "/Users/artechphy/Library/Android/sdk"))
jdk = Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"))
build = sdk / "build-tools/35.0.0"
platform = sdk / "platforms/android-35/android.jar"
output.mkdir(parents=True, exist_ok=True)
(output / "classes").mkdir(exist_ok=True)
(output / "dex").mkdir(exist_ok=True)
env = {**os.environ, "JAVA_HOME": str(jdk)}
def run(*args): subprocess.run([str(a) for a in args], check=True, env=env)
run(jdk / "bin/javac", "-source", "8", "-target", "8", "-encoding", "UTF-8", "-classpath", platform,
    "-d", output / "classes", source / "UiBridge.java")
run(build / "d8", "--lib", platform, "--min-api", "23", "--output", output / "dex",
    *sorted((output / "classes").rglob("*.class")))
run(build / "aapt", "package", "-f", "-M", source / "AndroidManifest.xml", "-I", platform,
    "-F", output / "unsigned.apk")
run("zip", "-q", "-j", output / "unsigned.apk", output / "dex/classes.dex")
run(build / "zipalign", "-f", "4", output / "unsigned.apk", output / "bridge.apk")
key = output / "test-only.jks"
if not key.exists():
    run(jdk / "bin/keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "test", "-dname", "CN=NotePad UI Test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "7")
run(build / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
    "--ks-key-alias", "test", output / "bridge.apk")
print(output / "bridge.apk")
