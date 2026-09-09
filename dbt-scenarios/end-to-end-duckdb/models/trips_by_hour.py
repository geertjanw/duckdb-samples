# A Python model, run IN-PROCESS by the same DuckDB — no separate compute, the
# data never leaves. Reads staging as a DataFrame and summarizes trips by the
# hour of day they were picked up.
def model(dbt, session):
    dbt.config(materialized="table")

    df = dbt.ref("stg_trips").df()

    out = (
        df.groupby("pickup_hour")
        .agg(
            trips=("fare_amount", "count"),
            avg_fare=("fare_amount", "mean"),
            avg_tip=("tip_amount", "mean"),
        )
        .reset_index()
        .sort_values("pickup_hour")
    )
    out["avg_fare"] = out["avg_fare"].round(2)
    out["avg_tip"] = out["avg_tip"].round(2)
    return out
