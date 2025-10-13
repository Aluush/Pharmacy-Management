# TODO: Pharmacy Management Roadmap and Backlog

Purpose: Track planned features and implementation tasks to guide ongoing contributions.

How to use:
- Check items off as they are completed.
- Break down larger items into subtasks as needed.
- Keep database, UI (FXML), and controller/service tasks linked in commits.

## Phase 1: Inventory, Procurement, and Stock Control

- [ ] Batch and expiry tracking
  - [ ] Data model: item_batches (id, item_id, batch_no, expiry_date, qty_on_hand, purchase_price, sell_price, location)
  - [ ] UI: Inventory view shows batches for selected item
  - [ ] UI: Color-code near-expiry and expired batches
  - [ ] Logic: FIFO consumption per sale; prevent selling expired stock
- [ ] Low stock and expiry alerts
  - [ ] Reorder levels per item; preferred supplier field
  - [ ] Background checks on app start and periodic timer
  - [ ] Dashboard alerts panel/badge for low stock and near expiry
- [ ] Suppliers and purchase orders
  - [ ] Tables: suppliers, purchase_orders, purchase_order_items, goods_receipts
  - [ ] Flow: Create PO -> Receive Goods (GRN) -> increment batches/stock -> update costs
  - [ ] UI: PO creation and GRN forms
- [ ] Inventory movement ledger
  - [ ] inventory_movements (id, item_batch_id, qty, movement_type, ref_type, ref_id, timestamp, user_id)
  - [ ] Record on sale, GRN, adjustments, returns; audit-friendly
- [ ] Barcode support and label printing
  - [ ] Integrate ZXing for barcode generation
  - [ ] POS scanning to add items/batches by barcode
- [ ] Price tiers and unit conversions (optional)
  - [ ] Define pack/box/strip conversions
  - [ ] Wholesale vs retail price tiers per item/batch

## Phase 2: POS, Customers, Prescriptions, and Returns

- [ ] Enhanced POS
  - [ ] Multi-payment methods (cash/card/mobile)
  - [ ] Discounts (per line and per invoice), tax handling
  - [ ] Price override with role-based permission
  - [ ] Hold/Resume cart; quick search by barcode/name
  - [ ] Receipt printing (thermal template)
- [ ] Returns and refunds
  - [ ] sales_returns, sales_return_items referencing original sale lines/batches
  - [ ] Stock increment back with movement entry; mark as damaged if not resellable
- [ ] Customers/patients and prescriptions
  - [ ] Tables: customers, prescriptions, prescription_items (dosage, frequency, duration)
  - [ ] Attach sale to prescription; basic validation vs duration
- [ ] Loyalty and credit (optional)
  - [ ] Points accrual/redeem; credit limit; statements and aging

## Phase 3: Reporting and Analytics

- [ ] Core reports
  - [ ] Sales summary (day/week/month), top items/customers, margins
  - [ ] Inventory valuation (FIFO) and stock on hand
  - [ ] Expiry report (by month/quarter)
  - [ ] Purchase history by supplier
  - [ ] Tax report; daily Z report for cash reconciliation
- [ ] Export
  - [ ] CSV/Excel (Apache POI)
  - [ ] PDF invoices/receipts (OpenPDF)

## Phase 4: Admin, Security, and Settings

- [ ] Role-based access control (RBAC)
  - [ ] users, roles, user_roles, permissions
  - [ ] Enforce permissions (e.g., price override, returns, adjustments)
  - [ ] Password hashing (BCrypt)
- [ ] Audit trail
  - [ ] audit_logs (user_id, action, entity, before/after, timestamp)
  - [ ] Hook into service layer operations
- [ ] Settings
  - [ ] settings table (key/value) for tax, company profile, currency, logo
  - [ ] Editable in Settings view
- [ ] Backup/restore
  - [ ] Scheduled SQL dump (local MySQL)
  - [ ] Restore workflow with confirmation

## Phase 5: UX and Internationalization

- [ ] Keyboard-centric POS; global shortcuts
- [ ] Toasts/notifications and validation cues
- [ ] Theme toggle (light/dark)
- [ ] Localization via ResourceBundle (e.g., English/French/Arabic)

## Database Schema (High-Level)

- items: id, name, generic_name, category_id, barcode, dosage_form, strength, tax_rate_id, reorder_level
- item_batches: id, item_id, batch_no, expiry_date, qty_on_hand, purchase_price, sell_price, location
- suppliers: id, name, phone, email, address
- purchase_orders: id, supplier_id, status, ordered_at, expected_at
- purchase_order_items: id, po_id, item_id, qty, price
- goods_receipts: id, po_id, received_at, user_id
- inventory_movements: id, item_batch_id, qty, type (SALE, GRN, ADJUST, RETURN), ref_type, ref_id, created_at, user_id
- customers: id, name, phone, dob, address
- prescriptions: id, customer_id, doctor_name, issued_at, notes
- prescription_items: id, prescription_id, item_id, dosage, frequency, duration
- sales: id, customer_id, total, subtotal, tax, discount, paid_amount, payment_method, created_at, user_id
- sale_items: id, sale_id, item_batch_id, qty, unit_price, discount
- sales_returns: id, sale_id, total_refund, reason, created_at, user_id
- sales_return_items: id, sales_return_id, sale_item_id, item_batch_id, qty, refund_amount
- users, roles, user_roles, permissions, audit_logs, settings

## Technical Guidelines

- Architecture
  - [ ] Introduce service and repository layers between controllers and Database.java
  - [ ] Use PreparedStatements and transactions; define transactional boundaries (POS, GRN, returns)
- Concurrency
  - [ ] Use background tasks for DB work; Platform.runLater for UI updates
- Observability
  - [ ] Central logging (slf4j + logback)
  - [ ] Log all inventory movements and errors
- Validation
  - [ ] Reusable validators for numeric inputs, expiry date, stock availability, prescription quantities
- Packaging/CI
  - [ ] GitHub Actions: mvn -B -DskipTests verify
  - [ ] jpackage for native installers (per OS)
- Libraries
  - ZXing (barcode), ControlsFX (UI), Apache POI (Excel), OpenPDF (PDF), BCrypt (hashing), HikariCP (pooling)

## Immediate Next Steps (Backlog for Next PRs)

- [ ] Implement Suppliers + Purchase Orders + Goods Receipts + Inventory Movements (data model, services, UI)
- [ ] Add Batch/Expiry UI and logic with low-stock/expiry alerts on Dashboard
- [ ] Introduce basic Users/Roles and guard sensitive actions (price override, returns, adjustments)
- [ ] POS enhancements: discounts, multi-payment, receipt printing

## Stretch Ideas

- [ ] Multi-store support with transfer orders
- [ ] Offline-first caching/sync
- [ ] Analytics dashboard with charts (e.g., Top 10 items, sales trends)
