# Business logic, in Python, running on GraalPy inside the JVM.
# `rows` comes straight from DuckDB as a Java array -- no copy, no JSON.
# Only builtins are used here, so no external Python packages are needed.

def tier(revenue):
    if revenue >= 14000:
        return "A"
    if revenue >= 12000:
        return "B"
    return "C"


def build_report(rows):
    lines = []
    lines.append(f"{'Region':<7} {'Orders':>6} {'Revenue':>10} {'AvgOrder':>9}  Tier")
    lines.append("-" * 42)

    total = 0.0
    for row in rows:
        region = str(row[0])
        orders = int(row[1])
        revenue = float(row[2])
        avg_order = float(row[3])
        total += revenue
        lines.append(f"{region:<7} {orders:>6} {revenue:>10.2f} {avg_order:>9.2f}  {tier(revenue)}")

    lines.append("-" * 42)
    lines.append(f"Total revenue across {len(rows)} regions: {total:.2f}")
    return "\n".join(lines)
