# AGENTS.md

## Project

This workspace contains a Windows build and repair of the Juicebox desktop application.

- Upstream baseline: Juicebox `v2.17.00`
- Upstream commit: `c7b6988cddfca060c5cac156147f1d9d8dcd5102`
- Canonical working source: this repository root (`src`, `build.xml`)
- Canonical runtime/compiler: JDK 25 at `D:\runtime\jdk-25`
- Ant: `.build-tools\apache-ant-1.10.15`

`juicebox-source-v2.17.00-pristine` is an unmodified reference checkout only. Do not use it as the active source tree or add fixes there. The root `juicebox.jar` is an old binary reference and must not be overwritten without an explicit request.

## Build

When working in the shared parent workspace, the same source is at `juicebox-source-v2.17.00-jdk25`. After cloning this repository, use the repository root. Build the active source with JDK 25:

```powershell
$env:JAVA_HOME = 'D:\runtime\jdk-25'
$env:ANT_HOME = (Resolve-Path '.build-tools\apache-ant-1.10.15').Path
& "$env:ANT_HOME\bin\ant.bat" `
  '-f' 'build.xml' `
  '-Dskip.tests=true' `
  '-Djdk.home.1.8=D:\runtime\jdk-25' `
  'all'
```

The `jdk.home.1.8` property name is inherited from the upstream Ant file. In this workspace it is intentionally pointed at JDK 25. Do not download or restore JDK 8 unless the user explicitly asks for a compatibility comparison.

The GUI artifact is:

`out\artifacts\Juicebox_jar\Juicebox.jar`

Run it with:

```powershell
& 'D:\runtime\jdk-25\bin\javaw.exe' `
  -jar 'out\artifacts\Juicebox_jar\Juicebox.jar'
```

## Required fixes

Keep these fixes in the active source and in future rebuilds:

1. Assembly autosave paths must use `java.io.File.getName()` or equivalent platform-safe logic. Never split dataset paths using a literal `/`; Windows paths use `\\`.
2. The GUI uber-JAR must include `lib/general/commons-math3-3.6.1.jar`; otherwise loading a `.hic` matrix fails with `NoClassDefFoundError: org/apache/commons/math3/linear/RealMatrix`.
3. Reset the process-wide Hi-C scale when opening a new `.hic`, preserve the loaded Hi-C scale while importing assembly annotations, and restore the file scale when leaving assembly mode.
4. Guard scaffold tooltip code when the assembly tracker is not initialized.
5. Detect reviewed assembly files containing `:::fragment_` or `:::debris` and direct users to the modified-assembly importer when an initial assembly is already loaded.

## Assembly workflow

For the supplied JBAT files:

1. Open the `.hic` file.
2. Use **Assembly → Import Map Assembly** for the original `.assembly` file.
3. Use **Assembly → Import Modified Assembly** for the `.review.assembly` file.

Do not load a reviewed file through **Import Map Assembly**. That treats the review order as the Hi-C coordinate order and makes the blue superscaffold outlines diverge from the heatmap.

Before changing an assembly file, validate it with the Juicebox assembly helper:

```powershell
python C:\Users\xiaox\.codex\skills\juicebox-assembly\scripts\assembly_tool.py validate <file.assembly>
```

Preserve header IDs, component lengths, signed orientations, and one-to-one component coverage unless the user explicitly requests a structural edit.

## Verification

At minimum, after a source or packaging change:

- Confirm Ant reports `BUILD SUCCESSFUL`.
- Check that the output JAR contains `org/apache/commons/math3/linear/RealMatrix.class`.
- Read a representative `.hic` matrix with `juicebox.tools.HiCTools` and require exit code `0`.
- Run the Windows assembly-path probe or an equivalent import test so `AssemblyStateTracker` does not throw `ArrayIndexOutOfBoundsException`.
- Keep generated logs and temporary probes outside `src`.

## File and change hygiene

- Use `apply_patch` for source edits.
- Keep the pristine reference tree unchanged.
- Do not replace the root reference JAR when producing a new build; use the active source's `out\artifacts` output.
- Do not remove user data, assembly files, or reference artifacts unless the user explicitly requests that exact cleanup.
