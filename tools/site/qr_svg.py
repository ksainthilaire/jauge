#!/usr/bin/env python3
"""Draws a QR code as a pure SVG (no library). Byte mode, error correction M, versions 1 to 6 (up to 84 bytes).

    python3 tools/site/qr_svg.py "https://jauge.io/telecharger" > code.svg

The algorithm follows the QR specification (ISO/IEC 18004): Reed-Solomon codewords over GF(256), function patterns,
zigzag placement, the eight masks and their penalty score, BCH format bits. The SVG uses `currentColor`, so the page
colors the code with CSS. The landing page (web/index.html) inlines the result of this script.
"""
import sys

# Level M, versions 1 to 6: (number of blocks, data codewords per block, error correction codewords per block).
BLOCKS = {1: (1, 16, 10), 2: (1, 28, 16), 3: (1, 44, 26), 4: (2, 32, 18), 5: (2, 43, 24), 6: (4, 27, 16)}
ALIGNMENT = {1: [], 2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30], 6: [6, 34]}


def multiply(x: int, y: int) -> int:
    """Product in GF(256) with the QR primitive polynomial 0x11D."""
    z = 0
    for i in range(7, -1, -1):
        z = (z << 1) ^ ((z >> 7) * 0x11D)
        z ^= ((y >> i) & 1) * x
    return z


def error_correction(data: list, degree: int) -> list:
    """Reed-Solomon remainder of `data` for a generator of the given degree."""
    generator = [0] * (degree - 1) + [1]
    root = 1
    for _ in range(degree):
        for j in range(len(generator)):
            generator[j] = multiply(generator[j], root)
            if j + 1 < len(generator):
                generator[j] ^= generator[j + 1]
        root = multiply(root, 2)
    remainder = [0] * degree
    for byte in data:
        factor = byte ^ remainder.pop(0)
        remainder.append(0)
        for i, coefficient in enumerate(generator):
            remainder[i] ^= multiply(coefficient, factor)
    return remainder


def codewords(text: bytes) -> tuple:
    """Chooses the version and returns (version, interleaved data and error correction codewords)."""
    for version in range(1, 7):
        blocks, data_per_block, ec_per_block = BLOCKS[version]
        capacity_bits = blocks * data_per_block * 8
        if 4 + 8 + 8 * len(text) <= capacity_bits:
            break
    else:
        raise ValueError("text too long for version 6 (84 bytes at most)")
    bits = []

    def append(value: int, length: int) -> None:
        bits.extend((value >> (length - 1 - i)) & 1 for i in range(length))

    append(0b0100, 4)
    append(len(text), 8)
    for byte in text:
        append(byte, 8)
    append(0, min(4, capacity_bits - len(bits)))
    append(0, (8 - len(bits) % 8) % 8)
    pad = 0xEC
    while len(bits) < capacity_bits:
        append(pad, 8)
        pad = 0x11 if pad == 0xEC else 0xEC
    data = [int("".join(map(str, bits[i:i + 8])), 2) for i in range(0, len(bits), 8)]
    block_data = [data[i * data_per_block:(i + 1) * data_per_block] for i in range(blocks)]
    block_ec = [error_correction(block, ec_per_block) for block in block_data]
    result = [block[i] for i in range(data_per_block) for block in block_data]
    result += [block[i] for i in range(ec_per_block) for block in block_ec]
    return version, result


def mask_bit(mask: int, x: int, y: int) -> bool:
    """True when the mask inverts the module at column x, row y."""
    return [
        (x + y) % 2 == 0, y % 2 == 0, x % 3 == 0, (x + y) % 3 == 0,
        (x // 3 + y // 2) % 2 == 0, x * y % 2 + x * y % 3 == 0,
        (x * y % 2 + x * y % 3) % 2 == 0, ((x + y) % 2 + x * y % 3) % 2 == 0,
    ][mask]


class Matrix:
    """The modules of one QR code: `dark[y][x]` and `function[y][x]` (reserved by a pattern)."""

    def __init__(self, version: int):
        self.version = version
        self.size = version * 4 + 17
        self.dark = [[False] * self.size for _ in range(self.size)]
        self.function = [[False] * self.size for _ in range(self.size)]

    def set_function(self, x: int, y: int, dark: bool) -> None:
        self.dark[y][x] = dark
        self.function[y][x] = True

    def draw_patterns(self) -> None:
        size = self.size
        for i in range(size):
            self.set_function(6, i, i % 2 == 0)
            self.set_function(i, 6, i % 2 == 0)
        for cx, cy in ((3, 3), (size - 4, 3), (3, size - 4)):
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < size and 0 <= y < size:
                        distance = max(abs(dx), abs(dy))
                        self.set_function(x, y, distance not in (2, 4))
        positions = ALIGNMENT[self.version]
        last = len(positions) - 1
        for i, cy in enumerate(positions):
            for j, cx in enumerate(positions):
                if (i, j) in ((0, 0), (0, last), (last, 0)):
                    continue
                for dy in range(-2, 3):
                    for dx in range(-2, 3):
                        self.set_function(cx + dx, cy + dy, max(abs(dx), abs(dy)) != 1)
        self.draw_format(0)

    def draw_format(self, mask: int) -> None:
        data = mask  # level M: format bits 00, then the mask
        remainder = data
        for _ in range(10):
            remainder = (remainder << 1) ^ ((remainder >> 9) * 0x537)
        bits = ((data << 10) | remainder) ^ 0x5412
        bit = lambda i: (bits >> i) & 1 == 1  # noqa: E731
        size = self.size
        for i in range(6):
            self.set_function(8, i, bit(i))
        self.set_function(8, 7, bit(6))
        self.set_function(8, 8, bit(7))
        self.set_function(7, 8, bit(8))
        for i in range(9, 15):
            self.set_function(14 - i, 8, bit(i))
        for i in range(8):
            self.set_function(size - 1 - i, 8, bit(i))
        for i in range(8, 15):
            self.set_function(8, size - 15 + i, bit(i))
        self.set_function(8, size - 8, True)

    def draw_data(self, data: list) -> None:
        size = self.size
        i = 0
        right = size - 1
        while right >= 1:
            if right == 6:
                right -= 1
            for vertical in range(size):
                for j in range(2):
                    x = right - j
                    upward = ((right + 1) & 2) == 0
                    y = size - 1 - vertical if upward else vertical
                    if not self.function[y][x] and i < len(data) * 8:
                        self.dark[y][x] = (data[i >> 3] >> (7 - (i & 7))) & 1 == 1
                        i += 1
            right -= 2

    def apply_mask(self, mask: int) -> None:
        for y in range(self.size):
            for x in range(self.size):
                if not self.function[y][x] and mask_bit(mask, x, y):
                    self.dark[y][x] = not self.dark[y][x]

    def penalty(self) -> int:
        size = self.size
        score = 0
        lines = [row[:] for row in self.dark] + [[self.dark[y][x] for y in range(size)] for x in range(size)]
        for line in lines:
            run_color, run_length = None, 0
            for value in line + [None]:
                if value == run_color:
                    run_length += 1
                else:
                    if run_length >= 5:
                        score += 3 + run_length - 5
                    run_color, run_length = value, 1
            pattern = "".join("1" if value else "0" for value in line)
            score += 40 * (pattern.count("10111010000") + pattern.count("00001011101"))
        for y in range(size - 1):
            for x in range(size - 1):
                if self.dark[y][x] == self.dark[y][x + 1] == self.dark[y + 1][x] == self.dark[y + 1][x + 1]:
                    score += 3
        dark_count = sum(sum(row) for row in self.dark)
        total = size * size
        k = (abs(dark_count * 20 - total * 10) + total - 1) // total - 1
        return score + 10 * k


def modules(text: str) -> Matrix:
    """The best masked matrix for the text."""
    version, data = codewords(text.encode("utf-8"))
    best, best_score = None, None
    for mask in range(8):
        matrix = Matrix(version)
        matrix.draw_patterns()
        matrix.draw_data(data)
        matrix.apply_mask(mask)
        matrix.draw_format(mask)
        score = matrix.penalty()
        if best_score is None or score < best_score:
            best, best_score = matrix, score
    return best


def svg(text: str, title: str) -> str:
    """An SVG of the QR code with a 4 module quiet zone, drawn with `currentColor`."""
    matrix = modules(text)
    quiet = 4
    total = matrix.size + 2 * quiet
    path = []
    for y, row in enumerate(matrix.dark):
        for x, dark in enumerate(row):
            if dark:
                path.append(f"M{x + quiet} {y + quiet}h1v1h-1z")
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total} {total}" role="img" aria-labelledby="qr-title" '
        f'shape-rendering="crispEdges"><title id="qr-title">{title}</title>'
        f'<path fill="currentColor" d="{"".join(path)}"/></svg>'
    )


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: qr_svg.py <text> [title]")
    print(svg(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else sys.argv[1]))
