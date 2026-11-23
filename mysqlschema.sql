CREATE DATABASE IF NOT EXISTS personal_expense_db;
USE personal_expense_db;

DROP TABLE IF EXISTS expenses;

CREATE TABLE expenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'Food',
    cost_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    total_cost DECIMAL(10, 2) GENERATED ALWAYS AS (cost_price * quantity) STORED,
    expense_date DATE NOT NULL DEFAULT (CURRENT_DATE)
);

-- Sample test entries (total_cost calculates automatically)
INSERT INTO expenses (product_name, category, cost_price, quantity, expense_date)
VALUES 
    ('Iced Coffee', 'Food', 85.00, 1, CURRENT_DATE),
    ('Notebook Pack', 'Personal', 45.50, 3, CURRENT_DATE);

SELECT * FROM expenses;