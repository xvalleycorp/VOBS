#!/usr/bin/env python3
"""Sinh logo VOBS (hướng H3: chữ V từ hai mắt xích móc vào nhau) dưới dạng SVG.

Hai mắt xích là hai viên nang (đường tâm bán kính R, nét T) dài bằng nhau, trục nghiêng ±THETA độ so với phương
đứng. Đầu dưới hai mắt chồng lên nhau (E) để mắt này luồn qua lòng mắt kia; mắt phải dịch lên DY để đầu mắt nằm gọn
trong lòng mắt trái. Hai mắt cắt nhau ở hai điểm; đi dọc mắt trái, giao điểm thứ nhất mắt trái nằm trên, giao điểm
thứ hai mắt phải nằm trên. Khe hở GAP được cắt bằng mặt nạ theo mép mắt nằm trên, giới hạn trong vòng tròn quanh giao
điểm.

Chạy: python3 website/tools/logo.py   (in ra logo.svg, favicon.svg và đoạn SVG cho header, hero)
"""
import math
import sys

# Logo chính: lòng mắt rộng hơn thanh khoảng 17% (xích chặt), nét dài
MAIN = dict(theta=30, length=38, r=7.6, t=7, e=-3, dy=4, gap=1.0)
# Favicon: thanh mảnh hơn, lòng rộng hơn để vẫn thấy hai mắt xích ở 16px
FAVICON = dict(theta=30, length=32, r=8.4, t=5.4, e=-3.2, dy=4, gap=1.8)
LIGHT = ("#C2693A", "#E9A33D")  # Đồng, Hổ phách
SAMPLES = 60


def capsule(p, q, r):
    (px, py), (qx, qy) = p, q
    length = math.hypot(qx - px, qy - py)
    ux, uy = (qx - px) / length, (qy - py) / length
    nx, ny = -uy, ux
    a, b = (px + nx * r, py + ny * r), (qx + nx * r, qy + ny * r)
    c, d = (qx - nx * r, qy - ny * r), (px - nx * r, py - ny * r)
    f = lambda pt: f"{pt[0]:.2f} {pt[1]:.2f}"
    path = f"M{f(a)}L{f(b)}A{r:.2f} {r:.2f} 0 0 0 {f(c)}L{f(d)}A{r:.2f} {r:.2f} 0 0 0 {f(a)}Z"
    pts, ang = [], math.atan2(ny, nx)
    for i in range(SAMPLES):
        pts.append((a[0] + (b[0] - a[0]) * i / SAMPLES, a[1] + (b[1] - a[1]) * i / SAMPLES))
    for i in range(SAMPLES):
        th = ang - math.pi * i / SAMPLES
        pts.append((qx + r * math.cos(th), qy + r * math.sin(th)))
    for i in range(SAMPLES):
        pts.append((c[0] + (d[0] - c[0]) * i / SAMPLES, c[1] + (d[1] - c[1]) * i / SAMPLES))
    for i in range(SAMPLES):
        th = ang + math.pi - math.pi * i / SAMPLES
        pts.append((px + r * math.cos(th), py + r * math.sin(th)))
    return path, pts


def crossings(a_pts, b_pts):
    """Giao điểm giữa hai đường tâm, xếp theo vị trí dọc mắt trái."""
    out = []
    for i in range(len(a_pts)):
        x1, y1 = a_pts[i]
        x2, y2 = a_pts[(i + 1) % len(a_pts)]
        for j in range(len(b_pts)):
            x3, y3 = b_pts[j]
            x4, y4 = b_pts[(j + 1) % len(b_pts)]
            den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            if abs(den) < 1e-12:
                continue
            t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
            u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / den
            if 0 <= t <= 1 and 0 <= u <= 1:
                p = (x1 + t * (x2 - x1), y1 + t * (y2 - y1))
                if all(math.hypot(p[0] - q[0], p[1] - q[1]) > 1.5 for q, _ in out):
                    out.append((p, i))
    return [p for p, _ in sorted(out, key=lambda item: item[1])]


def geometry(theta, length, r, t, e, dy, gap):
    th = math.radians(theta)
    u_l, u_r = (math.sin(th), math.cos(th)), (-math.sin(th), math.cos(th))
    lq, rq = (e, 0), (-e, -dy)
    lp = (lq[0] - u_l[0] * length, lq[1] - u_l[1] * length)
    rp = (rq[0] - u_r[0] * length, rq[1] - u_r[1] * length)
    # Co dãn cho vừa khung 56 trong lưới 64
    o = r + t / 2
    xs, ys = [lp[0], lq[0], rp[0], rq[0]], [lp[1], lq[1], rp[1], rq[1]]
    minx, maxx, miny, maxy = min(xs) - o, max(xs) + o, min(ys) - o, max(ys) + o
    k = 56 / max(maxx - minx, maxy - miny)
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    tr = lambda p: (32 + (p[0] - cx) * k, 32 + (p[1] - cy) * k)
    left, left_pts = capsule(tr(lp), tr(lq), r * k)
    right, right_pts = capsule(tr(rp), tr(rq), r * k)
    return dict(left=left, right=right, t=t * k, gap=gap, cross=crossings(left_pts, right_pts))


def mark(params, prefix):
    """Trả về (defs, path phải, path trái, độ dày nét)."""
    g = geometry(**params)
    defs, cut_left, cut_right = [], [], []
    for k, p in enumerate(g["cross"]):
        over = "L" if k % 2 == 0 else "R"
        cid = f"{prefix}-k{k}"
        defs.append(f'<clipPath id="{cid}"><circle cx="{p[0]:.2f}" cy="{p[1]:.2f}" '
                    f'r="{g["t"] * 1.05 + g["gap"]:.2f}"/></clipPath>')
        cut = (f'<path d="{g["left"] if over == "L" else g["right"]}" stroke="#000" '
               f'stroke-width="{g["t"] + 2 * g["gap"]:.2f}" clip-path="url(#{cid})"/>')
        (cut_right if over == "L" else cut_left).append(cut)
    box = 'maskUnits="userSpaceOnUse" x="0" y="0" width="64" height="64"'
    white = '<rect width="64" height="64" fill="#fff"/>'
    defs.append(f'<mask id="{prefix}-l" {box}>{white}<g fill="none">{"".join(cut_left)}</g></mask>')
    defs.append(f'<mask id="{prefix}-r" {box}>{white}<g fill="none">{"".join(cut_right)}</g></mask>')
    return "".join(defs), g["right"], g["left"], g["t"]


def standalone(params, prefix):
    defs, right, left, t = mark(params, prefix)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="VOBS">\n'
            f'  <defs>{defs}</defs>\n  <g fill="none" stroke-width="{t:.2f}">\n'
            f'    <path d="{right}" stroke="{LIGHT[1]}" mask="url(#{prefix}-r)"/>\n'
            f'    <path d="{left}" stroke="{LIGHT[0]}" mask="url(#{prefix}-l)"/>\n  </g>\n</svg>\n')


def inline(prefix, css_class):
    """Đoạn SVG dùng class CSS thay cho màu cố định, để đổi màu theo giao diện sáng, tối."""
    defs, right, left, t = mark(MAIN, prefix)
    return (f"<defs>{defs}</defs>\n"
            f'<g class="{css_class}" stroke-width="{t:.2f}"><path class="{css_class}-r" d="{right}" '
            f'mask="url(#{prefix}-r)"/><path class="{css_class}-l" d="{left}" mask="url(#{prefix}-l)"/></g>')


if __name__ == "__main__":
    out = sys.stdout
    out.write("=== static/assets/logo.svg (thêm lại chú thích ở đầu file nếu ghi đè)\n" + standalone(MAIN, "m"))
    out.write("\n=== static/assets/favicon.svg\n" + standalone(FAVICON, "f"))
    out.write("\n=== templates/_header.html (bên trong <svg class=\"logo\" viewBox=\"0 0 64 64\">)\n"
              + inline("logo", "logo-links"))
    out.write("\n\n=== templates/home.html, ô VOBS ở hero (bên trong <g transform=\"translate(224.8 150) scale(1.1)\">)\n"
              + inline("hub", "hub-links") + "\n")
