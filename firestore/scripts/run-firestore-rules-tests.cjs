const fs = require("node:fs");
const path = require("node:path");
const net = require("node:net");
const { spawn, spawnSync } = require("node:child_process");

const firebaseCli = path.resolve("node_modules", "firebase-tools", "lib", "bin", "firebase.js");
const env = { ...process.env };
const javaHome = detectJava21Home();

if (!javaHome) {
  console.error(
    "Firestore emulator tests require JDK 21+. Set JAVA_HOME to a JDK 21 install or install Android Studio's bundled JBR."
  );
  process.exit(1);
}

env.JAVA_HOME = javaHome;
env.PATH = `${path.join(javaHome, "bin")}${path.delimiter}${env.PATH ?? ""}`;

let emulator;
let exitCode = 1;

run().then(
  (code) => {
    exitCode = code;
  },
  (error) => {
    console.error(error instanceof Error ? error.message : error);
    exitCode = 1;
  }
).finally(async () => {
  await stopEmulator();
  process.exit(exitCode);
});

async function run() {
  emulator = spawn(
    process.execPath,
    [firebaseCli, "emulators:start", "--project", "demo-hom-e", "--only", "firestore"],
    {
      env,
      stdio: "inherit",
    }
  );

  await waitForPort(8080, 60_000);

  const testExitCode = await new Promise((resolve, reject) => {
    const child = spawn(
      process.execPath,
      ["--test", "firestore/tests/firestore.rules.test.cjs"],
      {
        env,
        stdio: "inherit",
      }
    );

    child.on("error", reject);
    child.on("exit", (code) => resolve(code ?? 1));
  });

  return testExitCode;
}

async function stopEmulator() {
  if (!emulator || emulator.killed) {
    return;
  }

  await new Promise((resolve) => {
    emulator.once("exit", () => resolve());
    emulator.kill("SIGINT");
    setTimeout(() => {
      if (!emulator.killed) {
        emulator.kill("SIGTERM");
      }
    }, 5_000);
  });
}

function detectJava21Home() {
  const candidates = [];

  if (process.env.JAVA_HOME) {
    candidates.push(process.env.JAVA_HOME);
  }

  if (process.platform === "win32") {
    candidates.push("C:\\Program Files\\Android\\Android Studio\\jbr");
    candidates.push("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2024.1.1\\jbr");
  }

  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }

    const javaExecutable = path.join(candidate, "bin", process.platform === "win32" ? "java.exe" : "java");
    if (!fs.existsSync(javaExecutable)) {
      continue;
    }

    const version = readJavaFeatureVersion(javaExecutable);
    if (version >= 21) {
      return candidate;
    }
  }

  return null;
}

function readJavaFeatureVersion(javaExecutable) {
  const versionProbe = spawnSync(javaExecutable, ["-version"], { encoding: "utf8" });
  if (versionProbe.error) {
    return 0;
  }

  const output = `${versionProbe.stdout ?? ""}\n${versionProbe.stderr ?? ""}`;
  const match = output.match(/version "(\d+)(?:\.\d+)?/);
  return match ? Number.parseInt(match[1], 10) : 0;
}

function waitForPort(port, timeoutMs) {
  const startedAt = Date.now();

  return new Promise((resolve, reject) => {
    const tryConnect = () => {
      const socket = net.createConnection({ host: "127.0.0.1", port });

      socket.once("connect", () => {
        socket.end();
        resolve();
      });

      socket.once("error", () => {
        socket.destroy();
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error(`Timed out waiting for Firestore emulator on port ${port}.`));
          return;
        }

        setTimeout(tryConnect, 500);
      });
    };

    tryConnect();
  });
}
