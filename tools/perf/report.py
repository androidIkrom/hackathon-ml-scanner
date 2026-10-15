"""One table per screen from a PerfTour run: frames a second, where a frame's time goes, the first frame, memory,
CPU and heat. Usage: python report.py logcat.txt samples.txt"""
import re
import sys
from datetime import datetime

STAMP = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d)\.(\d{3})")
TOUR = re.compile(r"Tour: (\S+)")
WINDOW = re.compile(
    r"Perf (\w+): ([\d.]+) fps (\d+x\d+), convert (\d+) ms, detect (\d+) ms, after (\d+) ms, total (\d+) ms(.*)$")
FIRST = re.compile(r"Perf (\w+): first frame (\d+) ms after start, detect (\d+) ms")
YOLO = re.compile(r"Perf YOLO: prepare (\d+) ms, model (\d+) ms, read (\d+) ms on (\w+)")
SAMPLE = re.compile(r"SAMPLE (\d\d-\d\d \d\d:\d\d:\d\d) pss=(\d+) cpu=([\d.]+) temp=(\d+)")


def when(day_time, millis="000"):
    return datetime.strptime(f"2000-{day_time}.{millis}", "%Y-%m-%d %H:%M:%S.%f")


def mean(xs):
    return sum(xs) / len(xs) if xs else 0


def main(logcat, samples):
    stops, windows, firsts, yolo, extra = [], [], [], [], []
    for line in open(logcat, encoding="utf-8", errors="replace"):
        m = STAMP.match(line)
        if not m:
            continue
        t = when(m.group(1), m.group(2))
        if (x := TOUR.search(line)) and "NungilPerf" in line:
            stops.append((t, x.group(1)))
        elif x := WINDOW.search(line):
            windows.append((t, x))
        elif x := YOLO.search(line):
            yolo.append((t, x))
        elif x := FIRST.search(line):
            firsts.append((t, x))
        elif "warmed up" in line or "Displayed com.nungil" in line or "FATAL" in line or "ANR in" in line:
            extra.append(line.strip())
    rows = []
    for line in open(samples, encoding="utf-8", errors="replace"):
        if x := SAMPLE.search(line):
            rows.append((when(x.group(1)), int(x.group(2)), float(x.group(3)), int(x.group(4))))

    def within(items, i):
        start = stops[i][0]
        end = stops[i + 1][0] if i + 1 < len(stops) else datetime.max
        return [it for it in items if start <= it[0] < end]

    print(f"{'screen':<11} {'fps':>5} {'size':>9} {'conv':>5} {'det':>5} {'after':>6} {'total':>6} "
          f"{'1st fr':>7} {'1st det':>7} {'PSS MB':>7} {'max':>5} {'CPU%':>5} {'temp':>9}")
    for i, (_, name) in enumerate(stops):
        if name in ("home", "done"):
            continue
        # The first window holds the model load; the rest are the steady state.
        w = [x for _, x in within(windows, i)][1:] or [x for _, x in within(windows, i)]
        f = [x for _, x in within(firsts, i)]
        s = within(rows, i)
        num = lambda k: mean([float(x.group(k)) for x in w])
        temps = [r[3] for r in s]
        print(f"{name:<11} {num(2):5.1f} {w[0].group(3) if w else '-':>9} {num(4):5.0f} {num(5):5.0f} {num(6):6.0f} "
              f"{num(7):6.0f} {f[0].group(2) if f else '-':>7} {f[0].group(3) if f else '-':>7} "
              f"{mean([r[1] for r in s]) / 1024:7.0f} {max([r[1] for r in s], default=0) / 1024:5.0f} "
              f"{mean([r[2] for r in s]):5.0f} {(f'{temps[0] / 10:.1f}-{temps[-1] / 10:.1f}') if temps else '-':>9}")
        model = {x.group(8).strip() for x in w if x.group(8).strip()}
        if model:
            print(f"{'':<11} {', '.join(sorted(model))}")
        y = [x for _, x in within(yolo, i)][1:] or [x for _, x in within(yolo, i)]
        if y:
            ym = lambda k: mean([float(x.group(k)) for x in y])
            print(f"{'':<11} YOLO: prepare {ym(1):.0f} ms, model {ym(2):.0f} ms, read {ym(3):.0f} ms on {y[-1].group(4)}")
    for line in extra:
        print(line)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
