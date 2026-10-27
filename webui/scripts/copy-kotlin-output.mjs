import { cpSync, existsSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const webuiRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const kotlinOutput = resolve(
  webuiRoot,
  "../build/artifacts/CompiledWebArtifact/webuijsrelease/kotlin-output",
);
const localOutput = resolve(webuiRoot, "build/kotlin-output");

if (!existsSync(resolve(kotlinOutput, "webui.mjs"))) {
  throw new Error(`Kotlin JS entry not found in ${kotlinOutput}`);
}

rmSync(localOutput, { recursive: true, force: true });
cpSync(kotlinOutput, localOutput, { recursive: true });
