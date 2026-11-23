package com.tracker;

import com.formdev.flatlaf.FlatLightLaf;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.DocumentFilter;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;

public class ExpenseTrackerApp extends JFrame {

    private static final String[] CATEGORIES = {
            "Food", "Transport", "Bills", "Groceries", "Personal", "Other"
    };

    private static final String[] FILTER_CATEGORIES = {
            "All", "Food", "Transport", "Bills", "Groceries", "Personal", "Other"
    };

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("₱#,##0.00");
    private static final DecimalFormat PLAIN_DECIMAL = new DecimalFormat("0.00");

    // Left Form Controls
    private final JTextField txtProductName = new JTextField();
    private final JComboBox<String> cmbCategory = new JComboBox<>(CATEGORIES);
    private final JTextField txtCostPrice = new JTextField();
    private final JSpinner spnQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
    private final JFormattedTextField txtQuantityField;
    private final DatePicker datePicker;
    private final JLabel lblLivePreview = new JLabel("₱0.00");

    // Right Table & Filter Controls
    private final JComboBox<String> cmbFilterCategory = new JComboBox<>(FILTER_CATEGORIES);
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel lblGrandTotal = new JLabel("Grand Total: ₱0.00");

    // Currently selected expense ID (-1 when none selected)
    private int selectedExpenseId = -1;

    public ExpenseTrackerApp() {
        setTitle("Personal Expense Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 640);
        setMinimumSize(new Dimension(920, 560));
        setLocationRelativeTo(null);

        // Configure number-only Cost Price and Quantity controls
        ((AbstractDocument) txtCostPrice.getDocument()).setDocumentFilter(
                new RegexDocumentFilter("^\\d*(\\.\\d{0,2})?$")
        );

        JSpinner.NumberEditor qtyEditor = new JSpinner.NumberEditor(spnQuantity, "#");
        spnQuantity.setEditor(qtyEditor);
        txtQuantityField = qtyEditor.getTextField();
        NumberFormat qtyFormat = NumberFormat.getIntegerInstance();
        qtyFormat.setGroupingUsed(false);
        NumberFormatter qtyFormatter = new NumberFormatter(qtyFormat) {
            private final DocumentFilter digitsOnlyFilter = new RegexDocumentFilter("^\\d*$");

            @Override
            protected DocumentFilter getDocumentFilter() {
                return digitsOnlyFilter;
            }
        };
        qtyFormatter.setValueClass(Integer.class);
        qtyFormatter.setMinimum(1);
        qtyFormatter.setMaximum(999999);
        qtyFormatter.setCommitsOnValidEdit(true);
        qtyFormatter.setAllowsInvalid(true);
        txtQuantityField.setFormatterFactory(new DefaultFormatterFactory(qtyFormatter));
        txtQuantityField.setValue(spnQuantity.getValue());

        // Configure Calendar DatePicker (calendar selection only, no manual typing)
        DatePickerSettings dateSettings = new DatePickerSettings();
        dateSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        dateSettings.setAllowKeyboardEditing(false);
        dateSettings.setAllowEmptyDates(false);
        datePicker = new DatePicker(dateSettings);
        datePicker.setDateToToday();
        datePicker.getComponentDateTextField().setFont(new Font("Segoe UI", Font.PLAIN, 13));
        datePicker.getComponentDateTextField().setPreferredSize(new Dimension(0, 32));
        datePicker.getComponentToggleCalendarButton().setPreferredSize(new Dimension(36, 32));
        datePicker.getComponentToggleCalendarButton().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Main root panel
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(new Color(245, 247, 250));
        setContentPane(root);

        // Top Header
        root.add(createHeaderPanel(), BorderLayout.NORTH);

        // Left Form Card
        root.add(createFormCard(), BorderLayout.WEST);

        // Right Table Panel
        String[] columns = {
                "ID", "Product Name", "Category", "Cost Price", "Quantity", "Total Cost", "Date"
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        root.add(createTableCard(), BorderLayout.CENTER);

        // Wire live preview and listeners
        attachLivePreviewListeners();
        attachTableSelectionListener();

        // Initial load from database
        loadExpenses();
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Personal Expense Tracker");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(30, 41, 59));

        JLabel subtitle = new JLabel("Track daily expenses with automatic MySQL total calculations");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(100, 116, 139));

        JPanel textGroup = new JPanel(new GridLayout(2, 1, 0, 2));
        textGroup.setOpaque(false);
        textGroup.add(title);
        textGroup.add(subtitle);

        header.add(textGroup, BorderLayout.WEST);
        return header;
    }

    private JPanel createFormCard() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(340, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel cardTitle = new JLabel("Expense Details");
        cardTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        cardTitle.setForeground(new Color(30, 41, 59));
        card.add(cardTitle, BorderLayout.NORTH);

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        int row = 0;
        row = addFormField(fieldsPanel, gbc, row, "Product Name", txtProductName);
        row = addFormField(fieldsPanel, gbc, row, "Category", cmbCategory);
        row = addFormField(fieldsPanel, gbc, row, "Cost Price (₱)", txtCostPrice);
        row = addFormField(fieldsPanel, gbc, row, "Quantity", spnQuantity);
        row = addFormField(fieldsPanel, gbc, row, "Expense Date", datePicker);

        // Live Preview Box
        JPanel previewBox = new JPanel(new BorderLayout());
        previewBox.setBackground(new Color(240, 253, 244));
        previewBox.setBorder(new CompoundBorder(
                new LineBorder(new Color(187, 247, 208), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel previewLabel = new JLabel("Calculated Total (Price × Qty)");
        previewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        previewLabel.setForeground(new Color(22, 101, 52));

        lblLivePreview.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLivePreview.setForeground(new Color(21, 128, 61));

        previewBox.add(previewLabel, BorderLayout.NORTH);
        previewBox.add(lblLivePreview, BorderLayout.CENTER);

        gbc.gridy = row++;
        gbc.insets = new Insets(10, 0, 4, 0);
        fieldsPanel.add(previewBox, gbc);

        // Push fields to top
        gbc.gridy = row;
        gbc.weighty = 1.0;
        fieldsPanel.add(Box.createVerticalGlue(), gbc);

        card.add(fieldsPanel, BorderLayout.CENTER);

        // Action Buttons (2x2 grid)
        JPanel btnPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        btnPanel.setOpaque(false);

        JButton btnAdd = createStyledButton("Add", new Color(37, 99, 235), Color.WHITE);
        JButton btnUpdate = createStyledButton("Update", new Color(13, 148, 136), Color.WHITE);
        JButton btnDelete = createStyledButton("Delete", new Color(220, 38, 38), Color.WHITE);
        JButton btnClear = createStyledButton("Clear", new Color(226, 232, 240), new Color(30, 41, 59));

        btnAdd.addActionListener(e -> addExpense());
        btnUpdate.addActionListener(e -> updateExpense());
        btnDelete.addActionListener(e -> deleteExpense());
        btnClear.addActionListener(e -> clearForm());

        btnPanel.add(btnAdd);
        btnPanel.add(btnUpdate);
        btnPanel.add(btnDelete);
        btnPanel.add(btnClear);

        card.add(btnPanel, BorderLayout.SOUTH);
        return card;
    }

    private int addFormField(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(71, 85, 105));

        gbc.gridy = row++;
        gbc.weighty = 0;
        gbc.insets = new Insets(6, 0, 3, 0);
        panel.add(label, gbc);

        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(0, 32));
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 4, 0);
        panel.add(field, gbc);

        return row;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(0, 36));
        return button;
    }

    private JPanel createTableCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(18, 18, 18, 18)
        ));

        // Top filter bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel tableTitle = new JLabel("Expense Records");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        tableTitle.setForeground(new Color(30, 41, 59));
        topBar.add(tableTitle, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);
        JLabel lblFilter = new JLabel("Category Filter:");
        lblFilter.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFilter.setForeground(new Color(71, 85, 105));

        cmbFilterCategory.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cmbFilterCategory.setPreferredSize(new Dimension(140, 30));
        cmbFilterCategory.addActionListener(e -> loadExpenses());

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnRefresh.setPreferredSize(new Dimension(85, 30));
        btnRefresh.addActionListener(e -> loadExpenses());

        filterPanel.add(lblFilter);
        filterPanel.add(cmbFilterCategory);
        filterPanel.add(btnRefresh);
        topBar.add(filterPanel, BorderLayout.EAST);

        card.add(topBar, BorderLayout.NORTH);

        // Configure JTable appearance
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(241, 245, 249));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(new Color(30, 58, 138));

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(new Color(51, 65, 85));
        header.setPreferredSize(new Dimension(0, 34));

        // Column alignments and widths
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(0).setMaxWidth(65);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(105);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(5).setPreferredWidth(115);
        table.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(new Color(226, 232, 240), 1));
        card.add(scrollPane, BorderLayout.CENTER);

        // Bottom Grand Total Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(new Color(248, 250, 252));
        bottomBar.setBorder(new CompoundBorder(
                new LineBorder(new Color(226, 232, 240), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel noteLabel = new JLabel("Click any row to edit or delete");
        noteLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        noteLabel.setForeground(new Color(100, 116, 139));

        lblGrandTotal.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblGrandTotal.setForeground(new Color(15, 23, 42));

        bottomBar.add(noteLabel, BorderLayout.WEST);
        bottomBar.add(lblGrandTotal, BorderLayout.EAST);

        card.add(bottomBar, BorderLayout.SOUTH);
        return card;
    }

    private void attachLivePreviewListeners() {
        DocumentListener previewUpdater = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLivePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLivePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLivePreview();
            }
        };
        txtCostPrice.getDocument().addDocumentListener(previewUpdater);
        txtQuantityField.getDocument().addDocumentListener(previewUpdater);
        spnQuantity.addChangeListener(e -> updateLivePreview());
        updateLivePreview();
    }

    private static final class RegexDocumentFilter extends DocumentFilter {
        private final String pattern;

        private RegexDocumentFilter(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) {
                return;
            }
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = current.substring(0, offset) + string + current.substring(offset);
            if (next.matches(pattern)) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String replacement = text == null ? "" : text;
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (next.matches(pattern)) {
                super.replace(fb, offset, length, replacement, attrs);
            }
        }
    }

    private void updateLivePreview() {
        try {
            String costText = txtCostPrice.getText().trim();
            String qtyText = txtQuantityField.getText().trim();
            if (costText.isEmpty() || ".".equals(costText) || qtyText.isEmpty()) {
                lblLivePreview.setText("₱0.00");
                return;
            }
            BigDecimal cost = new BigDecimal(costText);
            int qty = Integer.parseInt(qtyText);
            if (cost.compareTo(BigDecimal.ZERO) < 0 || qty < 0) {
                lblLivePreview.setText("₱0.00");
                return;
            }
            BigDecimal total = cost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            lblLivePreview.setText(CURRENCY_FORMAT.format(total));
        } catch (NumberFormatException ex) {
            lblLivePreview.setText("₱0.00");
        }
    }

    private void attachTableSelectionListener() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    selectedExpenseId = Integer.parseInt(tableModel.getValueAt(selectedRow, 0).toString());
                    txtProductName.setText(tableModel.getValueAt(selectedRow, 1).toString());
                    cmbCategory.setSelectedItem(tableModel.getValueAt(selectedRow, 2).toString());

                    String rawCost = tableModel.getValueAt(selectedRow, 3).toString()
                            .replace("₱", "").replace(",", "").trim();
                    txtCostPrice.setText(rawCost);

                    int qty = Integer.parseInt(tableModel.getValueAt(selectedRow, 4).toString());
                    spnQuantity.setValue(qty);

                    String dateStr = tableModel.getValueAt(selectedRow, 6).toString().trim();
                    if (!dateStr.isEmpty()) {
                        datePicker.setDate(LocalDate.parse(dateStr));
                    } else {
                        datePicker.setDateToToday();
                    }
                    updateLivePreview();
                }
            }
        });
    }

    private void loadExpenses() {
        tableModel.setRowCount(0);
        BigDecimal grandTotal = BigDecimal.ZERO;

        String selectedFilter = (String) cmbFilterCategory.getSelectedItem();
        boolean filterByCategory = selectedFilter != null && !"All".equalsIgnoreCase(selectedFilter);

        String sql = "SELECT id, product_name, category, cost_price, quantity, total_cost, expense_date "
                + "FROM expenses "
                + (filterByCategory ? "WHERE category = ? " : "")
                + "ORDER BY expense_date DESC, id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (filterByCategory) {
                pstmt.setString(1, selectedFilter);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String productName = rs.getString("product_name");
                    String category = rs.getString("category");
                    BigDecimal costPrice = rs.getBigDecimal("cost_price");
                    int quantity = rs.getInt("quantity");
                    BigDecimal totalCost = rs.getBigDecimal("total_cost");
                    Date expenseDate = rs.getDate("expense_date");

                    if (totalCost != null) {
                        grandTotal = grandTotal.add(totalCost);
                    }

                    tableModel.addRow(new Object[]{
                            id,
                            productName,
                            category,
                            costPrice != null ? "₱" + PLAIN_DECIMAL.format(costPrice) : "₱0.00",
                            quantity,
                            totalCost != null ? CURRENCY_FORMAT.format(totalCost) : "₱0.00",
                            expenseDate != null ? expenseDate.toString() : ""
                    });
                }
            }

            lblGrandTotal.setText("Grand Total: " + CURRENCY_FORMAT.format(grandTotal));

        } catch (SQLException ex) {
            lblGrandTotal.setText("Grand Total: ₱0.00");
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load expenses from MySQL:\n" + ex.getMessage(),
                    "Database Connection Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void addExpense() {
        ExpenseInput input = validateAndParseInput();
        if (input == null) {
            return;
        }

        // total_cost is GENERATED ALWAYS in MySQL, so it is omitted from INSERT
        String sql = "INSERT INTO expenses (product_name, category, cost_price, quantity, expense_date) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, input.productName);
            pstmt.setString(2, input.category);
            pstmt.setBigDecimal(3, input.costPrice);
            pstmt.setInt(4, input.quantity);
            pstmt.setDate(5, Date.valueOf(input.expenseDate));

            pstmt.executeUpdate();
            clearForm();
            loadExpenses();
            JOptionPane.showMessageDialog(this, "Expense added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error adding expense:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateExpense() {
        if (selectedExpenseId == -1) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select an expense row from the table to update.",
                    "No Row Selected",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        ExpenseInput input = validateAndParseInput();
        if (input == null) {
            return;
        }

        // total_cost is GENERATED ALWAYS in MySQL, so it is omitted from UPDATE
        String sql = "UPDATE expenses "
                + "SET product_name = ?, category = ?, cost_price = ?, quantity = ?, expense_date = ? "
                + "WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, input.productName);
            pstmt.setString(2, input.category);
            pstmt.setBigDecimal(3, input.costPrice);
            pstmt.setInt(4, input.quantity);
            pstmt.setDate(5, Date.valueOf(input.expenseDate));
            pstmt.setInt(6, selectedExpenseId);

            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                clearForm();
                loadExpenses();
                JOptionPane.showMessageDialog(this, "Expense updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error updating expense:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void deleteExpense() {
        if (selectedExpenseId == -1) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please select an expense row from the table to delete.",
                    "No Row Selected",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete expense ID #" + selectedExpenseId + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        String sql = "DELETE FROM expenses WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, selectedExpenseId);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                clearForm();
                loadExpenses();
                JOptionPane.showMessageDialog(this, "Expense deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error deleting expense:\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void clearForm() {
        selectedExpenseId = -1;
        txtProductName.setText("");
        cmbCategory.setSelectedIndex(0);
        txtCostPrice.setText("");
        spnQuantity.setValue(1);
        datePicker.setDateToToday();
        table.clearSelection();
        updateLivePreview();
    }

    private ExpenseInput validateAndParseInput() {
        String productName = txtProductName.getText().trim();
        if (productName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Product Name is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtProductName.requestFocus();
            return null;
        }

        String category = (String) cmbCategory.getSelectedItem();

        BigDecimal costPrice;
        try {
            String costText = txtCostPrice.getText().trim();
            if (costText.isEmpty() || ".".equals(costText)) {
                throw new NumberFormatException("Empty cost");
            }
            costPrice = new BigDecimal(costText).setScale(2, RoundingMode.HALF_UP);
            if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new NumberFormatException("Negative cost");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid non-negative Cost Price.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtCostPrice.requestFocus();
            return null;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(txtQuantityField.getText().trim());
            if (quantity <= 0) {
                throw new NumberFormatException("Non-positive quantity");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid Quantity (1 or greater).", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtQuantityField.requestFocus();
            return null;
        }

        LocalDate expenseDate = datePicker.getDate();
        if (expenseDate == null) {
            JOptionPane.showMessageDialog(this, "Please select an Expense Date from the calendar.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            datePicker.getComponentToggleCalendarButton().requestFocus();
            return null;
        }

        return new ExpenseInput(productName, category, costPrice, quantity, expenseDate);
    }

    private record ExpenseInput(
            String productName,
            String category,
            BigDecimal costPrice,
            int quantity,
            LocalDate expenseDate
    ) {}

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 10);
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatLaf: " + ex.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            ExpenseTrackerApp app = new ExpenseTrackerApp();
            app.setVisible(true);
        });
    }
}

