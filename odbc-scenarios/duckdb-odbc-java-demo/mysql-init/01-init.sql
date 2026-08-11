-- Small demo dataset loaded automatically when the container first starts.
USE demo;

CREATE TABLE customers (
    id      INT PRIMARY KEY,
    name    VARCHAR(100),
    country VARCHAR(50),
    revenue DECIMAL(12, 2)
);

INSERT INTO customers (id, name, country, revenue) VALUES
    (1,  'Acme Corp',        'NL', 125000.50),
    (2,  'Globex',           'US', 98000.00),
    (3,  'Initech',          'US', 45500.75),
    (4,  'Umbrella BV',      'NL', 210000.00),
    (5,  'Stark Industries', 'US', 560000.10),
    (6,  'Wayne Enterprises','US', 480000.00),
    (7,  'Tyrell NV',        'NL', 76000.25),
    (8,  'Soylent GmbH',     'DE', 33000.00),
    (9,  'Cyberdyne KG',     'DE', 154000.90),
    (10, 'Hooli SARL',       'FR', 89000.60),
    (11, 'Pied Piper',       'US', 12000.00),
    (12, 'Vandelay BV',      'NL', 67500.45);

-- Allow LOAD DATA LOCAL INFILE for the benchmark bulk load.
GRANT FILE ON *.* TO 'app_user'@'%';
FLUSH PRIVILEGES;
