import random

REGIONS = ["North", "South", "East", "West"]
PRODUCTS = ["Coffee", "Tea", "Juice", "Water"]


def sales_csv(n):
    """Return n rows of sales data as CSV text, header included.

    Python owns the data shape here; the browser's DuckDB-Wasm does every
    aggregation over the result. Seeded so each request returns the same set.
    """
    rnd = random.Random(42)
    rows = ["region,product,amount"]
    for _ in range(n):
        region = rnd.choice(REGIONS)
        product = rnd.choice(PRODUCTS)
        amount = round(rnd.uniform(5, 100), 2)
        rows.append(f"{region},{product},{amount}")
    return "\n".join(rows) + "\n"
