from marketmind.pipeline import DATA_DIR, inspect_datasets, write_dataset_report


if __name__ == "__main__":
    report = write_dataset_report(DATA_DIR)
    for dataset in inspect_datasets(DATA_DIR):
        missing = {
            column: count
            for column, count in dataset["missing_by_column"].items()
            if count
        }
        print(
            f"{dataset['file']}: {dataset['date_start']} to {dataset['date_end']}, "
            f"rows={dataset['rows']}, duplicate_dates={dataset['duplicate_dates_removed']}, "
            f"duplicate_records={dataset['duplicate_records']}, missing={missing or 'none'}"
        )
    print(
        f"Aligned model inputs: {report['alignment']['aligned_rows']} rows, "
        f"{report['alignment']['date_start']} to {report['alignment']['date_end']}"
    )