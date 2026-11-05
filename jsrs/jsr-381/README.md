# JSR 381 with DuckDB: a classifier whose model is a table

A JSR 381 (Java Visual Recognition API) `ImageClassifier` whose "model" is a
DuckDB table of labelled example images. Classifying an image is a
nearest-neighbour vote in SQL. It is a deliberately small implementation of the
spec's three-method contract, not a replacement for a neural network.

The article that goes with this sample explains the design.

## Prerequisites

- JDK 21 or newer. Check with `java -version`.
- Maven 3.9 or newer. Check with `mvn -v`.
- Images sorted into one subfolder per label (see step 2).

## Run it

Run every command from this folder (`jsrs/jsr-381`).

### 1. Compile

```bash
mvn -q compile
```

### 2. Arrange your images

Create one subfolder per label. The folder name becomes the label. PNG, JPEG,
GIF and BMP work. Use at least 5 images per label, because the classifier
votes over the 5 nearest examples.

```
images/
  sunset/   01.jpg 02.jpg ...
  ocean/    01.jpg 02.jpg ...
  forest/   01.jpg 02.jpg ...
```

The images are not included in this repo. Bring your own.

### 3. Train

```bash
mvn -q exec:java -Dexec.args="train images"
```

It prints `Stored N examples in model.duckdb`. Each run appends the images again,
so delete `model.duckdb` first to start over.

### 4. Classify

```bash
mvn -q exec:java -Dexec.args="classify test/ocean-1.png"
```

It prints one line per label among the 5 nearest neighbours, best first:

```
ocean        1.000
```

A mixed image shows split scores, for example `ocean 0.612` and `forest 0.388`.
Use an image that is not in the training folder, or the score is just the
nearest copy of itself.

### 5. Use it from your own code

```java
try (var c = new DuckDbImageClassifier(Path.of("model.duckdb"), new PixelEmbedder(), 5)) {
    Map<String, Float> scores = c.classify(Path.of("photo.jpg"));
}
```

## Inspect the model with SQL

`model.duckdb` is an ordinary DuckDB file. Stop the app first, because only one
process can write to it at a time. Then open it with the DuckDB CLI:

```bash
duckdb model.duckdb
```

Examples per label:

```sql
SELECT label, count(*) AS examples FROM examples GROUP BY label ORDER BY label;
```

Remove a mislabelled example:

```sql
DELETE FROM examples WHERE source = 'images/ocean/07.jpg';
```

The next `classify` call already reflects the change. More queries are in the
article.

## How it works

1. `PixelEmbedder` shrinks each image to 8×8 pixels and turns it into 192
   numbers, one red, green and blue value per pixel.
2. `train` inserts one row per example into the `examples` table.
3. `classify` embeds the new image and asks DuckDB for the 5 rows with the
   smallest cosine distance. Each neighbour's vote is weighted by its
   similarity, and the scores are normalised to sum to 1.

The embedder sets the accuracy ceiling. It separates images by colour layout,
so it handles "mostly blue" against "mostly green" well, and it cannot tell cats
from dogs. Put a better `Embedder` behind the same interface to improve it.
