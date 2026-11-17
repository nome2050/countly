"""Renders the Countly logo to PNG (pure Python, no dependencies).
Design: a countdown ring shaped like a "C" (opening on the right) with a pulse dot sitting in the gap,
on pure black. Geometry matches native/icons/*.xml and www/logo.svg (108 x 108 grid).
"""
import math, struct, sys, zlib

C1 = (142, 203, 255)   # #8ecbff
C2 = (195, 180, 255)   # #c3b4ff
GAP = math.radians(42)  # half-width of the opening
R, W = 24.0, 9.0        # ring radius / stroke width (108 grid)
DOT = (R, 0.0, 5.5)     # pulse dot (offset from center x, y, radius)
CENTER_DOT = 5.0


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(x, y, mono=False):
    """x, y on the 108 grid, centre at (54, 54). Returns (r,g,b,a) 0-255 for the logo, or None for empty."""
    cx, cy = x - 54, y - 54
    d = math.hypot(cx, cy)
    ang = math.atan2(cy, cx)  # 0 = east, +ve = clockwise on screen
    t = max(0.0, min(1.0, (cx + cy) / (2 * R) * 0.5 + 0.5))
    col = (255, 255, 255) if mono else lerp(C1, C2, t)
    on_ring = abs(d - R) <= W / 2 and abs(ang) >= GAP
    for sgn in (1, -1):  # round caps
        ex, ey = R * math.cos(GAP), sgn * R * math.sin(GAP)
        if math.hypot(cx - ex, cy - ey) <= W / 2:
            on_ring = True
    if on_ring:
        return (*col, 255)
    if math.hypot(cx - DOT[0], cy - DOT[1]) <= DOT[2]:
        return (*((255, 255, 255) if mono else (255, 255, 255)), 255)
    if d <= CENTER_DOT:
        return (*col, 150 if not mono else 255)
    if abs(d - R) <= W / 2:  # dim track inside the gap
        return (*col, 38)
    return None


def render(size, path, bg=(0, 0, 0), margin=0.0, rounded=0.0, transparent=False, mono=False):
    ss = 3
    scale = 108 / size
    rows = bytearray()
    for py in range(size):
        rows.append(0)
        for px in range(size):
            acc = [0.0, 0.0, 0.0, 0.0]
            for sy in range(ss):
                for sx in range(ss):
                    x = (px + (sx + .5) / ss) * scale
                    y = (py + (sy + .5) / ss) * scale
                    # logo grid coordinates, optionally shrunk by margin (used for launcher PNGs)
                    gx = 54 + (x - 54) / (1 - margin)
                    gy = 54 + (y - 54) / (1 - margin)
                    # rounded-square mask
                    inside = True
                    if rounded:
                        rr = rounded * 108
                        qx, qy = abs(x - 54) - (54 - rr), abs(y - 54) - (54 - rr)
                        inside = not (qx > 0 and qy > 0 and math.hypot(qx, qy) > rr)
                    if not inside:
                        continue
                    base = (0, 0, 0, 0) if transparent else (*bg, 255)
                    px_col = shade(gx, gy, mono)
                    if px_col:
                        a = px_col[3] / 255
                        r = px_col[0] * a + base[0] * (1 - a)
                        g = px_col[1] * a + base[1] * (1 - a)
                        b = px_col[2] * a + base[2] * (1 - a)
                        al = a + (base[3] / 255) * (1 - a)
                        acc[0] += r * al if transparent else r
                        acc[1] += g * al if transparent else g
                        acc[2] += b * al if transparent else b
                        acc[3] += al * 255
                    else:
                        acc[0] += base[0]; acc[1] += base[1]; acc[2] += base[2]; acc[3] += base[3]
            n = ss * ss
            if transparent:
                a = acc[3] / n
                k = (255 / a) if a else 0
                rows += bytes([min(255, int(acc[0] / n * k / (1)))if a else 0] * 0)  # placeholder (unused)
                rows += bytes((min(255, int(acc[0] / n / (a / 255) if a else 0)),
                               min(255, int(acc[1] / n / (a / 255) if a else 0)),
                               min(255, int(acc[2] / n / (a / 255) if a else 0)),
                               int(a)))
            else:
                rows += bytes((int(acc[0] / n), int(acc[1] / n), int(acc[2] / n), 255))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d))
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(bytes(rows), 9)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'www'
    render(192, f'{out}/icon-192.png', margin=0.12)
    render(512, f'{out}/icon-512.png', margin=0.12)
    
    print('logo rendered')
