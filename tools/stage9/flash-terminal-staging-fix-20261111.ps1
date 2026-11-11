[CmdletBinding()]
param(
    [ValidatePattern('^COM[0-9]+$')]
    [string]$Port,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$stage9Project = 'C:\Users\Ponomarenko\Documents\PlatformIO\Projects\edol-ams-terminal'
$stage9PioHome = 'C:\Users\Ponomarenko\.platformio'
$stage9Python = Join-Path $stage9PioHome 'penv\Scripts\python.exe'
$stage9Pio = Join-Path $stage9PioHome 'penv\Scripts\platformio.exe'
$stage9Esptool = Join-Path $stage9PioHome 'packages\tool-esptoolpy\esptool.py'
$stage9BuildRoot = Join-Path $stage9Project '.pio\staging-validation-20261111-'
$stage9Build = Join-Path $stage9BuildRoot 'esp32dev'
$stage9Ca = Join-Path $stage9Project '.pio\pre-enrollment-diagnostics\ca.pem'

# Use the audited image and the installed platform's standard esp32dev layout.
# This preflight reads only local artifacts, never a device or private config.
$stage9Preflight = @'
import hashlib, ssl, struct, sys
from pathlib import Path
root, build, pio = map(Path, sys.argv[1:])
expected = {
    root / 'platformio.ini': '168f5759d021c0287ebe9be51327c327f741457e0a5806154d8932bd85b4080c',
    build / 'firmware.bin': 'd76d7ed6e937dd7a605c4e86de1392f711cc0d76e244a55ca4e05e4077ec5874',
    build / 'firmware.elf': 'e544e653499dddacc25d517121b7ea2394ca1f68d95085658db8f6710261795c',
}
for path, digest in expected.items():
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise SystemExit('STOP: audited artifact/config changed: ' + path.name)
ca = (root / '.pio/pre-enrollment-diagnostics/ca.pem').read_text().strip()
if hashlib.sha256(ssl.PEM_cert_to_DER_cert(ca)).hexdigest() != 'a4b9c4effd45ee135e37eb6253280a6ffd2d619239884d8bb44765ed8e522839':
    raise SystemExit('STOP: retained CA changed')
data = (build / 'partitions.bin').read_bytes()
if len(data) != 0xc00:
    raise SystemExit('STOP: unexpected partition-table length')
parts = []
for position in range(0, len(data), 32):
    record = struct.unpack_from('<HBBII16sI', data, position)
    if record[0] == 0x50aa:
        parts.append((record[1], record[2], record[3], record[4], record[5].split(b'\0')[0]))
nvs = [part for part in parts if part[4] == b'nvs']
app = [part for part in parts if part[4] == b'app0']
if len(nvs) != 1 or nvs[0][2:4] != (0x9000, 0x5000):
    raise SystemExit('STOP: unexpected NVS layout')
if len(app) != 1 or app[0][2:4] != (0x10000, 0x140000):
    raise SystemExit('STOP: unexpected application layout')
if (build / 'firmware.bin').stat().st_size > app[0][3]:
    raise SystemExit('STOP: application exceeds its partition')
images = [(0x1000, build / 'bootloader.bin'), (0x8000, build / 'partitions.bin'),
          (0xe000, pio / 'packages/framework-arduinoespressif32/tools/partitions/boot_app0.bin'),
          (app[0][2], build / 'firmware.bin')]
for address, path in images:
    erase_start = address & ~0xfff
    erase_end = (address + path.stat().st_size + 0xfff) & ~0xfff
    for kind, subtype, offset, size, _ in parts:
        if kind == 1 and subtype != 0 and erase_start < offset + size and offset < erase_end:
            raise SystemExit('STOP: upload sectors overlap persistent data')
print('Artifact/CA/layout preflight PASS; upload sectors exclude NVS and filesystem.')
'@
& $stage9Python -c $stage9Preflight $stage9Project $stage9Build $stage9PioHome
if ($LASTEXITCODE -ne 0) { throw 'Local image preflight failed; no device accessed.' }
if ($CheckOnly) { return }
if ([string]::IsNullOrWhiteSpace($Port)) { throw 'Specify the confirmed terminal USB port with -Port COMxx.' }

# Enumerate only: reject absent, legacy or Bluetooth ports before opening one.
$stage9PortCheck = @'
import sys
from serial.tools import list_ports
ports = [port for port in list_ports.comports() if port.device.upper() == sys.argv[1].upper()]
if len(ports) != 1 or ports[0].vid is None or ports[0].pid is None:
    raise SystemExit('STOP: selected port is not a present USB serial device')
print('Confirmed USB serial port present:', ports[0].device)
'@
& $stage9Python -c $stage9PortCheck $Port
if ($LASTEXITCODE -ne 0) { throw 'USB port check failed; no device accessed.' }

# Read only the public partition table, not NVS/enrollment contents.
$stage9PartitionSnapshot = Join-Path $env:TEMP ('edol-terminal-partitions-' + [guid]::NewGuid().ToString('N') + '.bin')
& $stage9Python $stage9Esptool --chip esp32 --port $Port --baud 115200 `
    --before default_reset --after hard_reset read_flash 0x8000 0xC00 $stage9PartitionSnapshot
if ($LASTEXITCODE -ne 0) { throw 'Partition-table read failed; no firmware was written.' }
$stage9ExpectedTable = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $stage9Build 'partitions.bin')).Hash
$stage9ActualTable = (Get-FileHash -Algorithm SHA256 -LiteralPath $stage9PartitionSnapshot).Hash
if ($stage9ActualTable -ne $stage9ExpectedTable) {
    throw "Device partition table differs. STOP before upload. Retained public table: $stage9PartitionSnapshot"
}
Write-Host 'Device partition table matches. Waiting 20 seconds after the read/reset.'
Start-Sleep -Seconds 20

$stage9EnvNames = @('PLATFORMIO_BUILD_DIR', 'EDOL_TERMINAL_SECURE_BUILD', 'EDOL_AMS_ROOT_CA_FILE')
$stage9OldEnvironment = @{}
foreach ($stage9EnvName in $stage9EnvNames) {
    $stage9OldEnvironment[$stage9EnvName] = [Environment]::GetEnvironmentVariable($stage9EnvName, 'Process')
}
Push-Location -LiteralPath $stage9Project
try {
    $env:PLATFORMIO_BUILD_DIR = $stage9BuildRoot
    $env:EDOL_TERMINAL_SECURE_BUILD = '1'
    $env:EDOL_AMS_ROOT_CA_FILE = $stage9Ca
    # nobuild uploads the audited image; never use erase/uploadfs/sec06 targets.
    & $stage9Pio run -e esp32dev --target nobuild --target upload --upload-port $Port
    if ($LASTEXITCODE -ne 0) { throw 'Upload failed. Keep the output; do not erase or re-enroll.' }
    Write-Host 'Upload succeeded. Keep the terminal powered; verify its existing enrollment and state.'
    Write-Host 'Expected runtime ELF SHA-256: e544e653499dddacc25d517121b7ea2394ca1f68d95085658db8f6710261795c'
} finally {
    foreach ($stage9EnvName in $stage9EnvNames) {
        [Environment]::SetEnvironmentVariable($stage9EnvName, $stage9OldEnvironment[$stage9EnvName], 'Process')
    }
    Pop-Location
}
