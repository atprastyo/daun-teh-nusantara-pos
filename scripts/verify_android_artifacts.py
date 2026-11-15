#!/usr/bin/env python3
"""Read-only APK fingerprints and ELF alignment checks. Not runtime/signing approval."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile


def elf_load_alignments(data):
    if len(data) < 64 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError('Invalid or unsupported ELF header')
    wide = data[4] == 2
    endian = '<' if data[5] == 1 else '>'
    offset = struct.unpack_from(endian + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54 if wide else 42)
    if size < (56 if wide else 32) or offset + size * count > len(data):
        raise ValueError('Invalid ELF program-header bounds')
    alignments = []
    for index in range(count):
        at = offset + size * index
        if struct.unpack_from(endian + 'I', data, at)[0] == 1:  # PT_LOAD
            alignment = struct.unpack_from(endian + ('Q' if wide else 'I'), data, at + (48 if wide else 28))[0]
            if alignment < 16384 or alignment & (alignment - 1):
                raise ValueError('ELF PT_LOAD alignment is below 16 KiB or not a power of two')
            alignments.append(alignment)
    if not alignments:
        raise ValueError('No ELF load segments')
    return alignments


def fingerprint(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return {'path': str(path), 'bytes': path.stat().st_size, 'sha256': digest.hexdigest()}


def inspect_apk(path):
    libraries = []
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            if entry.filename.endswith('.so'):
                libraries.append({'path': entry.filename,
                                  'load_alignments': elf_load_alignments(archive.read(entry)),
                                  'zip_compressed': entry.compress_type != zipfile.ZIP_STORED})
    return {**fingerprint(path), 'native_libraries': libraries,
            'boundary': 'ELF alignment inspection only; signing, ZIP alignment and device runtime are separate checks.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/release/app-release-unsigned.apk'))
    parser.add_argument('--mapping', type=Path)
    parser.add_argument('--zipalign', type=Path, help='Installed Android build-tools zipalign executable')
    args = parser.parse_args()
    result = inspect_apk(args.apk)
    if args.mapping:
        result['mapping'] = fingerprint(args.mapping)
    if args.zipalign:
        subprocess.run([str(args.zipalign), '-c', '-P', '16', '4', str(args.apk)], check=True, capture_output=True)
        result['zip_alignment_16k'] = 'passed'
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
