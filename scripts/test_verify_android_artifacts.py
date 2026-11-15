import struct
import unittest
from verify_android_artifacts import elf_load_alignments


def fixture(wide, alignment=16384, kind=1, endian='<'):
    data = bytearray(64 + 56)
    data[:6] = b'\x7fELF' + bytes([2 if wide else 1, 1 if endian == '<' else 2])
    struct.pack_into(endian + ('Q' if wide else 'I'), data, 32 if wide else 28, 64)
    struct.pack_into(endian + 'HH', data, 54 if wide else 42, 56 if wide else 32, 1)
    struct.pack_into(endian + 'I', data, 64, kind)
    struct.pack_into(endian + ('Q' if wide else 'I'), data, 64 + (48 if wide else 28), alignment)
    return data


class ElfAcceptanceTests(unittest.TestCase):
    def test_32_and_64_bit_and_both_endiannesses(self):
        for wide in (False, True):
            for endian in ('<', '>'):
                self.assertEqual([16384], elf_load_alignments(fixture(wide, endian=endian)))

    def test_4k_and_non_power_alignment_rejected(self):
        for alignment in (0, 4096, 8192, 24576):
            with self.assertRaises(ValueError):
                elf_load_alignments(fixture(True, alignment))

    def test_missing_load_segment_rejected(self):
        with self.assertRaises(ValueError):
            elf_load_alignments(fixture(False, kind=2))

    def test_truncated_or_non_elf_input_rejected(self):
        for value in (b'', b'not an ELF' * 10, fixture(True)[:80]):
            with self.assertRaises(ValueError):
                elf_load_alignments(value)


if __name__ == '__main__':
    unittest.main()
