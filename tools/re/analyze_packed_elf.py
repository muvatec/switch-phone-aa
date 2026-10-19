#!/usr/bin/env python3
"""Static look at a packed 32-bit ARM ELF (no execution): headers, entropy, loader stub, syscalls, strings.

    python3 -m venv venv && venv/bin/pip install capstone
    venv/bin/python tools/re/analyze_packed_elf.py server.cgi

Used on the dongle's `server.cgi` (public 2024.10 build). Binaries are NOT stored in this repository.
"""
import collections, math, re, struct, sys
from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM

path = sys.argv[1]
d = open(path, "rb").read()
assert d[:4] == b"\x7fELF" and d[4] == 1, "expected a 32-bit ELF"

(e_type, e_machine, _v, e_entry, e_phoff, e_shoff, _f, _eh, e_phentsize, e_phnum, _shes, e_shnum, _shs) = struct.unpack("<HHIIIIIHHHHHH", d[16:52])
print(f"size={len(d)}  type={e_type} machine={e_machine} (40=ARM)  entry={e_entry:#x}  sections={e_shnum}")

loads = []
for i in range(e_phnum):
    t, off, va, _pa, fsz, msz, fl, al = struct.unpack("<IIIIIIII", d[e_phoff + i * e_phentsize : e_phoff + (i + 1) * e_phentsize])
    print(f" PH{i}: type={t:#x} off={off:#x} vaddr={va:#x} filesz={fsz:#x} memsz={msz:#x} flags={fl}")
    if t == 1 and fsz:
        loads.append((va, off, fsz))
base = loads[0][0]


def entropy(b):
    c = collections.Counter(b)
    n = len(b)
    return -sum(v / n * math.log2(v / n) for v in c.values())


print("\nentropy per 2 KB block (>7.5 = encrypted/compressed):")
print(" ".join(f"{entropy(d[i:i + 2048]):.1f}" for i in range(0, len(d), 2048)))

seg_end = loads[0][1] + loads[0][2]
print(f"\nbytes after the loaded segment: {len(d) - seg_end}")

entry_off = e_entry - base
md = Cs(CS_ARCH_ARM, CS_MODE_ARM)
code = list(md.disasm(d[entry_off:seg_end], e_entry))
print(f"\nloader: {len(code)} instructions decoded from the entry point")

print("\nsyscalls (r7 = number before svc):")
SYS = {0x05: "open", 0x06: "close", 0x36: "ioctl", 0x5B: "munmap", 0x7D: "mprotect", 0xC0: "mmap2", 0x04: "write", 0x01: "exit", 0xF0002: "ARM cacheflush"}
last = None
for i in code:
    if i.mnemonic == "mov" and i.op_str.startswith("r7, #"):
        last = int(i.op_str.split("#")[1], 0)
    elif i.mnemonic == "orr" and i.op_str.startswith("r7, r7, #") and last is not None:
        last |= int(i.op_str.split("#")[1], 0)
    elif i.mnemonic == "svc" and last is not None:
        print(f"  {i.address:#x}: {last:#x} {SYS.get(last, '?')}")
        last = None

print("\nstrings inside the loader area:")
for m in re.finditer(rb"[ -~]{6,}", d[entry_off:seg_end]):
    print(f"  {e_entry + m.start():#x}: {m.group().decode()[:80]}")
# strings built byte by byte with 'mov r0,#c ; strb r0,[...]' (e.g. the device node name)
chars = []
for i in code[:120]:
    if i.mnemonic == "mov" and i.op_str.startswith("r0, #0x") and 0x20 <= int(i.op_str.split("#")[1], 0) < 0x7F:
        chars.append(chr(int(i.op_str.split("#")[1], 0)))
print("  chars loaded one by one near the entry:", "".join(chars))
