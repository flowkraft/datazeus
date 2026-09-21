// @description Academy cubes: writes five Northwind Company cube definitions into config/cubes, wired to the academy schemas already installed on THIS connection. Idempotent — rewrites what it wrote before.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the database that holds northwind_co_<scale>
//   vendor — String (uppercase): POSTGRES, DUCKDB (the two supported here)
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, CUBES_DIR, PREFIX, connectionCode (supplied by the app)

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — which installed Northwind Company the cubes point at. 'S', 'M' or 'L'. The cubes read
// northwind_co_<scale> (the operational tables) and northwind_co_dw_<scale> (the star). Install
// those first: academy-northwind-co-install and academy-northwind-co-dw-install.
// params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// Where the cube folders go. A relative path is taken from the app's folder. config/cubes is the
// USER cubes directory that CubesService.listAll() always scans — config/samples-cubes, next to it,
// holds the product's five read-only samples and is deliberately left alone.
String CUBES_DIR = (params?.CUBES_DIR ?: 'config/cubes').toString()
// Folder-name prefix, which is also the cube id shown in the UI list.
String PREFIX = (params?.PREFIX ?: 'academy-northwind-co').toString()
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
//
// The product ships five sample cubes in config/samples-cubes, built on the frozen `northwind`
// sample: Sales, Inventory, Customers, HR and Sales Warehouse. They are good cube DEFINITIONS and
// they are what Data Warehousing Series 2 - 25 asks a learner to read. What they are not is good
// to RUN: that sample holds 3 employees, 7 orders and 13 order lines, its Territories, Region and
// EmployeeTerritories tables are empty, and its vw_sales_detail is a synthetic star that shares no
// keys with the tables beside it. Slice, dice, roll up and drill down all terminate immediately.
//
// This script writes the same five cubes against Northwind Company instead, so the four analytical
// moves have something to move through. Nothing here modifies the product's samples; the academy
// cubes land in config/cubes as ordinary user cubes and can be deleted with the folder.
//
// WHAT COMES OUT (in <CUBES_DIR>/)
//   <prefix>-sales/        one row per order line   — Orders x Order Details x Products x ...
//   <prefix>-inventory/    one row per product      — Products x Categories x Suppliers
//   <prefix>-customers/    one row per customer     — Customers x Orders x Order Details
//   <prefix>-hr/           one row per employee     — Employees x EmployeeTerritories x ...
//   <prefix>-warehouse/    one row per fact row     — fact_order_line x the six dimensions
// Each folder holds cube.xml (name, description, connectionId) and <id>-cube-config.groovy (the DSL).
//
// WHY THE TABLE NAMES LOOK LIKE THAT
// Every table is written fully qualified and pre-quoted: "northwind_co_s"."Order Details". The
// academy datasets live in their own schema, and CubeSqlGenerator.renderTableName() passes a name
// that already starts with a quote through verbatim instead of quoting it as ONE identifier — so
// a bare 'northwind_co_s.Orders' would render as "northwind_co_s.Orders", a table that does not
// exist. The same qualified token is used as the join name, because detectReferencedTables()
// matches dimension SQL against joinName + "." to decide which JOINs to emit. Verbose, but it is
// the one spelling that works in all three places, and these files are generated, not hand-kept.
//
// AND WHY THE COLUMN NAMES LOOK LIKE THAT
// Columns are quoted too: ${CUBE}."OrderID", not ${CUBE}.OrderID. The install script creates them
// as quoted mixed-case identifiers ("Quantity", "OrderID"), so PostgreSQL stores them mixed-case —
// and an unquoted reference there folds to lower case and finds nothing. DuckDB is forgiving about
// this and PostgreSQL is not, so a cube tested only on DuckDB would ship broken. The star's own
// columns are lower-case snake_case and would survive either way; they are quoted for consistency.
//
// IF YOU EDIT A TEMPLATE BELOW, keep its DSL strings SINGLE-quoted. The table tokens carry their
// own double quotes, so a double-quoted DSL string ends early and the file stops parsing; and
// single quotes are also what keeps ${CUBE} from being interpolated by this script instead of by
// the generator. Where the SQL itself needs a literal quote (LIKE '%Owner%'), escape it — which in
// THIS file means two backslashes, because one is eaten writing the template out.
//
// A CAVEAT WORTH KNOWING, which is the generator's and not this script's: segments become a WHERE
// clause AFTER the FROM clause is built, and detectReferencedTables() never looks at segment SQL.
// A segment that names a joined table therefore only works when some selected dimension or measure
// already pulled that join in. The sales and customers segments are inherited from the samples and
// behave that way; the warehouse segments are written as semi-joins against the cube source instead
// (identical results, since every dimension join is INNER on a NOT NULL key) so they always hold.
//
// WHAT IS DIFFERENT FROM THE SAMPLES, AND WHY
//   - Dates. The sample HR cube computes tenure with strftime('%Y','now'), which is SQLite's. The
//     academy cubes use extract(year from current_date), which DuckDB and PostgreSQL both accept.
//   - Columns the samples could not have. Orders.Channel and Orders.Status, Customers.Segment,
//     Products.UnitCost (a real margin measure) exist only in Northwind Company.
//   - The warehouse cube is not a copy. The sample reads one flat denormalized view; the academy
//     star is fact_order_line with six dimensions, two of them SCD Type 2, so it is written fresh.
//
// Design: kraft-src-company-biz/.docs/plan-academy-datasets.md (D1, D5) and the Data Warehousing
// curriculum, Series 2 - 25.
// ─────────────────────────────────────────────────────────────────────────────────────────────

String VENDOR = (vendor ?: '').toString().toUpperCase()
if (!(VENDOR in ['DUCKDB', 'POSTGRES'])) {
    throw new IllegalStateException(
        "academy-cubes supports DuckDB and PostgreSQL. This connection is ${VENDOR ?: 'unknown'}. " +
        "ClickHouse keeps the academy data in a database rather than a schema and its date functions " +
        "differ, so the generated SQL has not been proven there; nothing was written.")
}
if (!(SCALE in ['S', 'M', 'L'])) {
    throw new IllegalArgumentException("SCALE must be S, M or L (got '${SCALE}'); nothing was written.")
}

String SUFFIX     = SCALE.toLowerCase()
String OLTP       = "northwind_co_${SUFFIX}"
String STAR       = "northwind_co_dw_${SUFFIX}"
String CONNECTION = (params?.connectionCode ?: '').toString()

log.info("academy-cubes: scale ${SCALE}, operational schema ${OLTP}, star ${STAR}, vendor ${VENDOR}")

// ─────────────────────────────────────────────────────────────────────────────────────────────
// PREFLIGHT — a cube pointed at a table that is not there is worse than no cube, because it fails
// only when someone clicks it. Every table each cube needs is checked here, against this very
// connection, before a single file is written.
// ─────────────────────────────────────────────────────────────────────────────────────────────
Closure<Boolean> tableExists = { String schema, String table ->
    def row = dbSql.firstRow(
        'select count(*) as n from information_schema.tables where table_schema = ? and table_name = ?',
        [schema, table])
    return (row?.n ?: 0) as int > 0
}

List<String> OLTP_TABLES = ['Orders', 'Order Details', 'Customers', 'Products', 'Categories',
                            'Suppliers', 'Shippers', 'Employees', 'EmployeeTerritories',
                            'Territories', 'Region']
List<String> STAR_TABLES = ['fact_order_line', 'dim_date', 'dim_customer', 'dim_product',
                            'dim_employee', 'dim_shipper', 'dim_category']

List<String> missingOltp = OLTP_TABLES.findAll { !tableExists(OLTP, it) }
if (missingOltp) {
    throw new IllegalStateException(
        "Schema ${OLTP} is missing ${missingOltp.size()} table(s): ${missingOltp.join(', ')}. " +
        "Run academy-northwind-co-install at SCALE ${SCALE} on this connection first; nothing was written.")
}

List<String> missingStar = STAR_TABLES.findAll { !tableExists(STAR, it) }
boolean withWarehouse = missingStar.isEmpty()
if (!withWarehouse) {
    log.warn("academy-cubes: skipping the warehouse cube — ${STAR} is missing ${missingStar.join(', ')}. " +
             "Run academy-northwind-co-dw-install at SCALE ${SCALE} and re-run this script to add it.")
}

// A qualified, pre-quoted table token: "schema"."Table". Used identically as the cube source, as a
// join name, and as the prefix of every column reference — see WHY THE TABLE NAMES LOOK LIKE THAT.
Closure<String> qt = { String schema, String table -> "\"${schema}\".\"${table}\"" }

// Whole-year difference, the two vendors' shared spelling. SQLite's strftime is not available here.
Closure<String> yearsSince = { String expr -> "(extract(year from current_date) - extract(year from ${expr}))" }

// ─────────────────────────────────────────────────────────────────────────────────────────────
// THE CUBE TEMPLATES
// Written as single-quoted here-docs so that ${CUBE} — the generator's own placeholder for the
// cube source — survives into the file instead of being interpolated by THIS script. Everything
// this script fills in is an @MARKER@, substituted in one pass at the end.
// ─────────────────────────────────────────────────────────────────────────────────────────────

String SALES_DSL = '''// ═══════════════════════════════════════════════════════════════════════════
// Northwind Company Sales Analysis cube  —  GENERATED by datasets/scripts/academy-cubes.groovy
// ═══════════════════════════════════════════════════════════════════════════
//
// Cube source: @ORDERS@ (the order header table)
// Scale: @SCALE@ — @ORDERCOUNT@ orders, @LINECOUNT@ order lines
//
// Entire point: answer transactional sales questions — who bought what, when,
// for how much money, by which sales rep, on time or late, at what margin.
// This is the sample Sales cube's design, pointed at a company with five years
// of trading in it, so that slicing and drilling actually go somewhere.
//
// JOIN graph (validated by transitive join resolution):
//
//   @ORDERS@ (cube source)
//     ├── @OD@            (L1, parent=CUBE — one_to_many)
//     │     └── @PRODUCTS@   (L2, parent=Order Details — many_to_one)
//     │           ├── @CATEGORIES@ (L3, parent=Products — many_to_one)
//     │           └── @SUPPLIERS@  (L3, parent=Products — many_to_one)
//     ├── @CUSTOMERS@     (L1, parent=CUBE — many_to_one)
//     ├── @EMPLOYEES@     (L1, parent=CUBE — many_to_one)
//     └── @SHIPPERS@      (L1, parent=CUBE — many_to_one)
// ═══════════════════════════════════════════════════════════════════════════

cube {
  sql_table '@ORDERS@'
  title 'Northwind Company Sales Analysis (@SCALE@)'
  description 'Sales transactions by customer, employee, product, time and geography, over Northwind Company at scale @SCALE@'

  // ── Dimensions: Orders (cube source) ───────────────────────────────────
  dimension {
    name 'OrderID'
    title 'Order ID'
    description 'Unique order identifier'
    sql '${CUBE}."OrderID"'
    type 'number'
    primary_key true
  }
  dimension {
    name 'OrderDate'
    title 'Order Date'
    description 'When the order was placed'
    sql '${CUBE}."OrderDate"'
    type 'time'
  }
  dimension {
    name 'ShippedDate'
    title 'Shipped Date'
    description 'When the order shipped (NULL = not yet shipped, or never shipped during the courier outage)'
    sql '${CUBE}."ShippedDate"'
    type 'time'
  }
  dimension {
    name 'ShipCountry'
    title 'Ship Country'
    description 'Destination country'
    sql '${CUBE}."ShipCountry"'
    type 'string'
  }
  dimension {
    name 'ShipCity'
    title 'Ship City'
    description 'Destination city'
    sql '${CUBE}."ShipCity"'
    type 'string'
  }
  // ── Columns the frozen sample does not have ────────────────────────────
  dimension {
    name 'Status'
    title 'Order Status'
    description 'Open, Shipped or Cancelled — the order lifecycle state'
    sql '${CUBE}."Status"'
    type 'string'
  }
  dimension {
    name 'Channel'
    title 'Channel'
    description 'How the order reached us (sales rep, web, phone, EDI). Northwind Company only.'
    sql '${CUBE}."Channel"'
    type 'string'
  }

  // ── Dimensions: Order Details (L1 join, spaced identifier) ─────────────
  dimension {
    name 'Quantity'
    title 'Line Quantity'
    description 'Units sold per line item'
    sql '@OD@."Quantity"'
    type 'number'
  }
  dimension {
    name 'LineUnitPrice'
    title 'Line Unit Price'
    description 'Unit price actually paid (per line item)'
    sql '@OD@."UnitPrice"'
    type 'number'
  }
  dimension {
    name 'Discount'
    title 'Discount'
    description 'Discount applied to line item (0.0 to 1.0)'
    sql '@OD@."Discount"'
    type 'number'
  }

  // ── Dimensions: Products (L2 join via Order Details) ───────────────────
  dimension {
    name 'ProductName'
    title 'Product'
    description 'Product name'
    sql '@PRODUCTS@."ProductName"'
    type 'string'
  }

  // ── Dimensions: Categories (L3 join via Products) ─────────────────────
  dimension {
    name 'CategoryName'
    title 'Category'
    description 'Product category name'
    sql '@CATEGORIES@."CategoryName"'
    type 'string'
  }

  // ── Dimensions: Suppliers (L3 join via Products) ──────────────────────
  dimension {
    name 'SupplierName'
    title 'Supplier'
    description 'Supplier company name'
    sql '@SUPPLIERS@."CompanyName"'
    type 'string'
  }
  dimension {
    name 'SupplierCountry'
    title 'Supplier Country'
    description 'Country where the supplier is based'
    sql '@SUPPLIERS@."Country"'
    type 'string'
  }

  // ── Dimensions: Customers (L1 join) ────────────────────────────────────
  dimension {
    name 'CustomerCompanyName'
    title 'Customer'
    description 'Customer company name'
    sql '@CUSTOMERS@."CompanyName"'
    type 'string'
  }
  dimension {
    name 'CustomerCountry'
    title 'Customer Country'
    description 'Country where the customer is based'
    sql '@CUSTOMERS@."Country"'
    type 'string'
  }
  dimension {
    name 'CustomerCity'
    title 'Customer City'
    description 'City where the customer is based'
    sql '@CUSTOMERS@."City"'
    type 'string'
  }
  dimension {
    name 'CustomerSegment'
    title 'Customer Segment'
    description 'Wholesale, Retail, Restaurant and so on. Northwind Company only — and it CHANGES over time for some customers.'
    sql '@CUSTOMERS@."Segment"'
    type 'string'
  }

  // ── Dimensions: Employees (L1 join) ────────────────────────────────────
  dimension {
    name 'EmployeeName'
    title 'Sales Rep'
    description 'Employee who took the order (concatenated first + last name)'
    sql '@EMPLOYEES@."FirstName" || \\' \\' || @EMPLOYEES@."LastName"'
    type 'string'
  }

  // ── Dimensions: Shippers (L1 join) ─────────────────────────────────────
  dimension {
    name 'ShipperName'
    title 'Shipper'
    description 'Shipping company name'
    sql '@SHIPPERS@."CompanyName"'
    type 'string'
  }

  // ── Measures ────────────────────────────────────────────────────────────
  measure {
    name 'OrderCount'
    title 'Order Count'
    description 'Number of unique orders. PICK THIS WHEN: NOT grouping by OrderID. (At order grain it always returns 1, which is meaningless.)'
    sql '${CUBE}."OrderID"'
    type 'count_distinct'
  }
  // ── Revenue measures (two grain-specific names for the same SQL) ──
  // Both compute SUM(UnitPrice × Quantity × (1 - Discount)) across line items.
  // At customer / category / supplier / time grain that is total revenue, so
  // use Revenue. At order grain the SUM rolls up to a single order's value, so
  // use Order Value. Same math, different label.
  measure {
    name 'Revenue'
    title 'Revenue'
    description 'Sum of (UnitPrice × Quantity × (1 - Discount)) across all line items in scope. PICK THIS WHEN: grouping at customer / product / category / supplier / time grain.'
    sql '(@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount"))'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'OrderValue'
    title 'Order Value'
    description 'Total value of an individual order. PICK THIS WHEN: grouping by Order ID for invoice-ledger / per-order browsing.'
    sql '(@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount"))'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'GrossMargin'
    title 'Gross Margin'
    description 'Revenue minus cost of goods (UnitCost × Quantity). Northwind Company carries Products.UnitCost, so margin is a real measure here rather than an assumed one.'
    sql '((@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount")) - (@PRODUCTS@."UnitCost" * @OD@."Quantity"))'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'TotalQuantity'
    title 'Units Sold'
    description 'Total quantity sold across all line items'
    sql '@OD@."Quantity"'
    type 'sum'
  }
  measure {
    name 'AvgDiscount'
    title 'Average Discount'
    description 'Average discount applied across line items (0.0 to 1.0 — multiply by 100 for percent)'
    sql '@OD@."Discount"'
    type 'avg'
  }
  measure {
    name 'TotalFreight'
    title 'Total Freight'
    description 'Sum of freight charges. NOTE: this over-counts when joined to Order Details (one freight value is repeated per line item)'
    sql '${CUBE}."Freight"'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'UniqueCustomers'
    title 'Unique Customers'
    description 'Number of distinct customers'
    sql '${CUBE}."CustomerID"'
    type 'count_distinct'
  }
  measure {
    name 'UniqueProducts'
    title 'Unique Products'
    description 'Number of distinct products sold'
    sql '@OD@."ProductID"'
    type 'count_distinct'
  }

  // ── Joins (with parent chain for transitive resolution) ────────────────
  join {
    name '@OD@'
    parent 'CUBE'
    sql '${CUBE}."OrderID" = @OD@."OrderID"'
    relationship 'one_to_many'
  }
  join {
    name '@PRODUCTS@'
    parent '@OD@'
    sql '@OD@."ProductID" = @PRODUCTS@."ProductID"'
    relationship 'many_to_one'
  }
  join {
    name '@CATEGORIES@'
    parent '@PRODUCTS@'
    sql '@PRODUCTS@."CategoryID" = @CATEGORIES@."CategoryID"'
    relationship 'many_to_one'
  }
  join {
    name '@SUPPLIERS@'
    parent '@PRODUCTS@'
    sql '@PRODUCTS@."SupplierID" = @SUPPLIERS@."SupplierID"'
    relationship 'many_to_one'
  }
  join {
    name '@CUSTOMERS@'
    parent 'CUBE'
    sql '${CUBE}."CustomerID" = @CUSTOMERS@."CustomerID"'
    relationship 'many_to_one'
  }
  join {
    name '@EMPLOYEES@'
    parent 'CUBE'
    sql '${CUBE}."EmployeeID" = @EMPLOYEES@."EmployeeID"'
    relationship 'many_to_one'
  }
  join {
    name '@SHIPPERS@'
    parent 'CUBE'
    sql '${CUBE}."ShipVia" = @SHIPPERS@."ShipperID"'
    relationship 'many_to_one'
  }

  // ── Segments (named WHERE clauses) ─────────────────────────────────────
  segment {
    name 'shipped'
    title 'Shipped Orders'
    description 'Orders that have been shipped'
    sql '${CUBE}."ShippedDate" IS NOT NULL'
  }
  segment {
    name 'unshipped'
    title 'Not Yet Shipped'
    description 'Orders with no ship date. At this scale that is mostly the Speedy Express outage, not a backlog.'
    sql '${CUBE}."ShippedDate" IS NULL'
  }
  segment {
    name 'late_shipment'
    title 'Late Shipments'
    description 'Orders shipped after the customer-promised required date'
    sql '${CUBE}."ShippedDate" > ${CUBE}."RequiredDate"'
  }
  segment {
    name 'with_discount'
    title 'Discounted Lines'
    description 'Line items with a non-zero discount applied'
    sql '@OD@."Discount" > 0'
  }
  segment {
    name 'cancelled'
    title 'Cancelled Orders'
    description 'Orders that were cancelled. Northwind Company only.'
    sql '${CUBE}."Status" = \\'Cancelled\\''
  }

  // ── Hierarchies (drill-down paths) ─────────────────────────────────────
  hierarchy {
    name 'customer_geography'
    title 'Customer Geography'
    levels 'CustomerCountry', 'CustomerCity', 'CustomerCompanyName'
  }
  hierarchy {
    name 'ship_geography'
    title 'Ship-To Geography'
    levels 'ShipCountry', 'ShipCity'
  }
  hierarchy {
    name 'product_taxonomy'
    title 'Product Taxonomy'
    levels 'CategoryName', 'ProductName'
  }
}
'''

String INVENTORY_DSL = '''// ═══════════════════════════════════════════════════════════════════════════
// Northwind Company Product Inventory cube  —  GENERATED by datasets/scripts/academy-cubes.groovy
// ═══════════════════════════════════════════════════════════════════════════
//
// Cube source: @PRODUCTS@
// Scale: @SCALE@ — @PRODUCTCOUNT@ products across @CATEGORYCOUNT@ categories
//
// Entire point: manage the product catalogue and stock — what we have, what is
// running low, who supplies it, what the stock on hand is worth, and now what
// it COST us, because Northwind Company carries UnitCost beside UnitPrice.
//
// One row per product, a different grain from the Sales cube.
//
// JOIN graph (only L1 joins):
//
//   @PRODUCTS@ (cube source)
//     ├── @CATEGORIES@ (L1, parent=CUBE — many_to_one)
//     └── @SUPPLIERS@  (L1, parent=CUBE — many_to_one)
// ═══════════════════════════════════════════════════════════════════════════

cube {
  sql_table '@PRODUCTS@'
  title 'Northwind Company Product Inventory (@SCALE@)'
  description 'Product catalogue with stock levels, cost, categories and suppliers'

  // ── Dimensions: Products (cube source) ─────────────────────────────────
  dimension {
    name 'ProductID'
    title 'Product ID'
    description 'Unique product identifier'
    sql '${CUBE}."ProductID"'
    type 'number'
    primary_key true
  }
  dimension {
    name 'ProductName'
    title 'Product Name'
    description 'Name of the product'
    sql '${CUBE}."ProductName"'
    type 'string'
  }
  dimension {
    name 'QuantityPerUnit'
    title 'Pack Size'
    description 'Quantity per package (e.g. "10 boxes x 20 bags")'
    sql '${CUBE}."QuantityPerUnit"'
    type 'string'
  }
  dimension {
    name 'Discontinued'
    title 'Discontinued'
    description '1 if the product is no longer sold, 0 if active'
    sql '${CUBE}."Discontinued"'
    type 'number'
  }
  dimension {
    name 'UnitsInStock'
    title 'Units In Stock'
    description 'Current inventory count'
    sql '${CUBE}."UnitsInStock"'
    type 'number'
  }
  dimension {
    name 'UnitsOnOrder'
    title 'Units On Order'
    description 'Units currently on order from suppliers'
    sql '${CUBE}."UnitsOnOrder"'
    type 'number'
  }
  dimension {
    name 'ReorderLevel'
    title 'Reorder Level'
    description 'Minimum stock threshold before reordering (may be NULL)'
    sql '${CUBE}."ReorderLevel"'
    type 'number'
  }
  dimension {
    name 'UnitPrice'
    title 'Unit Price'
    description 'Catalogue price per unit'
    sql '${CUBE}."UnitPrice"'
    type 'number'
  }
  dimension {
    name 'UnitCost'
    title 'Unit Cost'
    description 'What the unit costs us. Northwind Company only — the frozen sample has no cost column at all.'
    sql '${CUBE}."UnitCost"'
    type 'number'
  }

  // ── Dimensions: Categories (L1 join) ───────────────────────────────────
  dimension {
    name 'CategoryName'
    title 'Category'
    description 'Product category name'
    sql '@CATEGORIES@."CategoryName"'
    type 'string'
  }

  // ── Dimensions: Suppliers (L1 join) ────────────────────────────────────
  dimension {
    name 'SupplierName'
    title 'Supplier'
    description 'Supplier company name'
    sql '@SUPPLIERS@."CompanyName"'
    type 'string'
  }
  dimension {
    name 'SupplierCountry'
    title 'Supplier Country'
    description 'Country where the supplier is based (sourcing strategy / compliance)'
    sql '@SUPPLIERS@."Country"'
    type 'string'
  }
  dimension {
    name 'SupplierCity'
    title 'Supplier City'
    description 'City where the supplier is based (concentration-risk analysis)'
    sql '@SUPPLIERS@."City"'
    type 'string'
  }

  // ── Measures ────────────────────────────────────────────────────────────
  // Most measures here are for category / supplier grain. At per-product grain
  // they degenerate: ProductCount = 1, AvgUnitPrice = that product's own price.
  measure {
    name 'ProductCount'
    title 'Product Count'
    description 'Number of distinct products. PICK THIS WHEN: grouping by category / supplier / segment. (At product grain it always returns 1.)'
    type 'count'
  }
  measure {
    name 'InventoryValue'
    title 'Inventory Value'
    description 'Retail value of stock on hand (UnitsInStock × UnitPrice). The classic inventory KPI.'
    sql '(${CUBE}."UnitsInStock" * ${CUBE}."UnitPrice")'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'InventoryCost'
    title 'Inventory Value at Cost'
    description 'What the stock on hand cost us (UnitsInStock × UnitCost). Compare against Inventory Value to see the margin locked up in the warehouse.'
    sql '(${CUBE}."UnitsInStock" * ${CUBE}."UnitCost")'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'TotalUnitsInStock'
    title 'Total Units In Stock'
    description 'Sum of all units currently in stock'
    sql '${CUBE}."UnitsInStock"'
    type 'sum'
  }
  measure {
    name 'TotalUnitsOnOrder'
    title 'Total Units On Order'
    description 'Sum of all units currently on order from suppliers'
    sql '${CUBE}."UnitsOnOrder"'
    type 'sum'
  }
  measure {
    name 'AvgUnitPrice'
    title 'Average Unit Price'
    description "Average catalogue price. PICK THIS WHEN: grouping by category / supplier. (At product grain it returns the product's own price.)"
    sql '${CUBE}."UnitPrice"'
    type 'avg'
    format 'currency'
  }
  measure {
    name 'UniqueSuppliers'
    title 'Unique Suppliers'
    description 'Number of distinct suppliers. PICK THIS WHEN: grouping by category / segment. (At product or supplier grain it always returns 1.)'
    sql '${CUBE}."SupplierID"'
    type 'count_distinct'
  }

  // ── Joins (L1 only) ─────────────────────────────────────────────────────
  join {
    name '@CATEGORIES@'
    parent 'CUBE'
    sql '${CUBE}."CategoryID" = @CATEGORIES@."CategoryID"'
    relationship 'many_to_one'
  }
  join {
    name '@SUPPLIERS@'
    parent 'CUBE'
    sql '${CUBE}."SupplierID" = @SUPPLIERS@."SupplierID"'
    relationship 'many_to_one'
  }

  // ── Segments ────────────────────────────────────────────────────────────
  segment {
    name 'in_stock'
    title 'In Stock'
    description 'Products with at least one unit in stock'
    sql '${CUBE}."UnitsInStock" > 0'
  }
  segment {
    name 'out_of_stock'
    title 'Out of Stock'
    description 'Products with zero units in stock'
    sql '${CUBE}."UnitsInStock" = 0'
  }
  segment {
    name 'reorder_needed'
    title 'Reorder Needed'
    description 'Products at or below their reorder level'
    sql '${CUBE}."UnitsInStock" <= ${CUBE}."ReorderLevel" AND ${CUBE}."ReorderLevel" > 0'
  }
  segment {
    name 'active'
    title 'Active Products'
    description 'Products that are not discontinued'
    sql '${CUBE}."Discontinued" = 0'
  }
  segment {
    name 'discontinued'
    title 'Discontinued'
    description 'Products that are discontinued'
    sql '${CUBE}."Discontinued" = 1'
  }

  // ── Hierarchies ─────────────────────────────────────────────────────────
  hierarchy {
    name 'product_taxonomy'
    title 'Product Taxonomy'
    levels 'CategoryName', 'ProductName'
  }
  hierarchy {
    name 'supplier_geography'
    title 'Supplier Geography'
    levels 'SupplierCountry', 'SupplierCity', 'SupplierName'
  }
}
'''

String CUSTOMERS_DSL = '''// ═══════════════════════════════════════════════════════════════════════════
// Northwind Company Customer Management cube  —  GENERATED by datasets/scripts/academy-cubes.groovy
// ═══════════════════════════════════════════════════════════════════════════
//
// Cube source: @CUSTOMERS@
// Scale: @SCALE@ — @CUSTOMERCOUNT@ customers
//
// Entire point: the CRM / sales-ops view — who the customers are, where they
// are, how active they are, and how much they spend. One row per customer.
//
// Why join Order Details? Because Orders has no total column: order value is
// COMPUTED from line items. Without this join the cube cannot answer "who are
// our biggest customers by revenue", which is the whole point of a CRM cube.
//
// JOIN graph (2-level chain):
//
//   @CUSTOMERS@ (cube source)
//     └── @ORDERS@   (L1, parent=CUBE — one_to_many)
//           └── @OD@ (L2, parent=Orders — one_to_many)
// ═══════════════════════════════════════════════════════════════════════════

cube {
  sql_table '@CUSTOMERS@'
  title 'Northwind Company Customer Management (@SCALE@)'
  description 'Customer base, order activity and revenue analysis for CRM and sales-ops'

  // ── Dimensions: Customers (cube source) ────────────────────────────────
  dimension {
    name 'CustomerID'
    title 'Customer ID'
    description 'Unique customer identifier (5-character code)'
    sql '${CUBE}."CustomerID"'
    type 'string'
    primary_key true
  }
  dimension {
    name 'CustomerCompanyName'
    title 'Company'
    description 'Customer company name'
    sql '${CUBE}."CompanyName"'
    type 'string'
  }
  dimension {
    name 'ContactName'
    title 'Contact Name'
    description 'Primary contact person at the customer'
    sql '${CUBE}."ContactName"'
    type 'string'
  }
  dimension {
    name 'ContactTitle'
    title 'Contact Title'
    description 'Job title of the primary contact (decision-maker analysis)'
    sql '${CUBE}."ContactTitle"'
    type 'string'
  }
  dimension {
    name 'Country'
    title 'Country'
    description 'Customer country'
    sql '${CUBE}."Country"'
    type 'string'
  }
  dimension {
    name 'City'
    title 'City'
    description 'Customer city'
    sql '${CUBE}."City"'
    type 'string'
  }
  dimension {
    name 'Region'
    title 'Region'
    description 'State or province. NULL where the country has none — which is a fact about the world, not a defect.'
    sql '${CUBE}."Region"'
    type 'string'
  }
  dimension {
    name 'Segment'
    title 'Segment'
    description 'Wholesale, Retail, Restaurant and so on. Northwind Company only.'
    sql '${CUBE}."Segment"'
    type 'string'
  }

  // ── Dimensions: Orders (L1 join — time slicing AND per-order browsing) ──
  dimension {
    name 'OrderID'
    title 'Order ID'
    description 'Unique order identifier. Pick this dimension to establish per-order grain for invoice/order-ledger browsing.'
    sql '@ORDERS@."OrderID"'
    type 'number'
  }
  dimension {
    name 'OrderDate'
    title 'Order Date'
    description 'When the order was placed (slice customers by ordering era, or list individual orders chronologically)'
    sql '@ORDERS@."OrderDate"'
    type 'time'
  }
  dimension {
    name 'OrderShippedDate'
    title 'Order Shipped Date'
    description 'When the order shipped (NULL = not yet shipped)'
    sql '@ORDERS@."ShippedDate"'
    type 'time'
  }
  dimension {
    name 'OrderChannel'
    title 'Order Channel'
    description 'How the order reached us. Northwind Company only.'
    sql '@ORDERS@."Channel"'
    type 'string'
  }

  // ── Measures ────────────────────────────────────────────────────────────
  measure {
    name 'CustomerCount'
    title 'Customer Count'
    description 'Number of distinct customers'
    sql '${CUBE}."CustomerID"'
    type 'count_distinct'
  }
  measure {
    name 'OrderCount'
    title 'Order Count'
    description 'Number of distinct orders placed'
    sql '@ORDERS@."OrderID"'
    type 'count_distinct'
  }
  // At customer grain this is the customer's total spend, so use Customer
  // Lifetime Value. At order grain the SUM rolls up to one order, so use
  // Order Value. Same math, different label.
  measure {
    name 'CustomerLifetimeValue'
    title 'Customer Lifetime Value (CLV)'
    description 'Total revenue from a customer across all their orders. PICK THIS WHEN: grouping by Customer (no OrderID).'
    sql '(@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount"))'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'OrderValue'
    title 'Order Value'
    description 'Total value of an individual order. PICK THIS WHEN: grouping by Order ID for invoice/order-ledger browsing.'
    sql '(@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount"))'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'AvgOrderValue'
    title 'Average Order Value'
    description "Average line revenue — grain-agnostic. At customer grain it shows avg line value per customer; at order grain the order's avg line value."
    sql '(@OD@."UnitPrice" * @OD@."Quantity" * (1 - @OD@."Discount"))'
    type 'avg'
    format 'currency'
  }
  measure {
    name 'EarliestOrderDate'
    title 'First Order'
    description 'Date of the earliest order from this customer — with five years of trading this is a real cohort marker'
    sql '@ORDERS@."OrderDate"'
    type 'min'
  }
  measure {
    name 'LatestOrderDate'
    title 'Most Recent Order'
    description 'Date of the most recent order from this customer (churn risk indicator)'
    sql '@ORDERS@."OrderDate"'
    type 'max'
  }

  // ── Joins (2-level chain) ──────────────────────────────────────────────
  join {
    name '@ORDERS@'
    parent 'CUBE'
    sql '${CUBE}."CustomerID" = @ORDERS@."CustomerID"'
    relationship 'one_to_many'
  }
  join {
    name '@OD@'
    parent '@ORDERS@'
    sql '@ORDERS@."OrderID" = @OD@."OrderID"'
    relationship 'one_to_many'
  }

  // ── Segments ────────────────────────────────────────────────────────────
  segment {
    name 'decision_makers'
    title 'Decision Makers'
    description 'Customers whose contact is an Owner or Manager (decision-makers)'
    sql '${CUBE}."ContactTitle" LIKE \\'%Owner%\\' OR ${CUBE}."ContactTitle" LIKE \\'%Manager%\\''
  }
  segment {
    name 'with_email'
    title 'Has Email Contact'
    description 'Customers with an email address on file (data-quality / outreach segment)'
    sql '${CUBE}."Email" IS NOT NULL AND ${CUBE}."Email" != \\'\\''
  }
  segment {
    name 'no_region'
    title 'No Region On File'
    description 'Customers whose Region is NULL. Useful for showing that COUNT(Region) and COUNT(*) disagree.'
    sql '${CUBE}."Region" IS NULL'
  }
  segment {
    name 'shipped_orders'
    title 'Shipped Orders'
    description 'Limit to orders that have been shipped'
    sql '@ORDERS@."ShippedDate" IS NOT NULL'
  }
  segment {
    name 'unshipped_orders'
    title 'Outstanding Orders'
    description 'Limit to orders with no ship date'
    sql '@ORDERS@."ShippedDate" IS NULL'
  }

  // ── Hierarchies ─────────────────────────────────────────────────────────
  hierarchy {
    name 'customer_geography'
    title 'Customer Geography'
    levels 'Country', 'City', 'CustomerCompanyName'
  }
  hierarchy {
    name 'contact_role'
    title 'Contact Role'
    levels 'ContactTitle', 'CustomerCompanyName'
  }
  hierarchy {
    name 'segment_geography'
    title 'Segment then Geography'
    levels 'Segment', 'Country', 'CustomerCompanyName'
  }
}
'''

String HR_DSL = '''// ═══════════════════════════════════════════════════════════════════════════
// Northwind Company Human Resources cube  —  GENERATED by datasets/scripts/academy-cubes.groovy
// ═══════════════════════════════════════════════════════════════════════════
//
// Cube source: @EMPLOYEES@
// Scale: @SCALE@ — @EMPLOYEECOUNT@ employees over @LEVELCOUNT@ reporting levels,
// covering @TERRITORYCOUNT@ territories in @REGIONCOUNT@ sales regions.
//
// Entire point: the HR view of the workforce — who works here, where, how long,
// what role, which territories they cover.
//
// This is the cube that gains most from leaving the frozen sample behind. There,
// Territories, Region and EmployeeTerritories are ALL EMPTY, so the territory
// hierarchy renders blank and the region measures return zero. Here they are
// populated, and the org chart is deep enough to have a shape.
//
// Dates: the sample computes tenure with strftime('%Y','now'), which is SQLite's
// and fails on DuckDB and PostgreSQL. These use extract(year from current_date),
// which both accept.
//
// JOIN graph (3-level chain):
//
//   @EMPLOYEES@ (cube source)
//     └── @EMPTERR@        (L1, parent=CUBE — one_to_many junction)
//           └── @TERRITORIES@ (L2, parent=EmployeeTerritories — many_to_one)
//                 └── @REGION@  (L3, parent=Territories — many_to_one)
// ═══════════════════════════════════════════════════════════════════════════

cube {
  sql_table '@EMPLOYEES@'
  title 'Northwind Company Human Resources (@SCALE@)'
  description 'Workforce composition, tenure, age and territory coverage'

  // ── Dimensions: Employees (cube source) ────────────────────────────────
  dimension {
    name 'EmployeeID'
    title 'Employee ID'
    description 'Unique employee identifier'
    sql '${CUBE}."EmployeeID"'
    type 'number'
    primary_key true
  }
  dimension {
    name 'EmployeeName'
    title 'Employee'
    description 'Full name (FirstName LastName)'
    sql '${CUBE}."FirstName" || \\' \\' || ${CUBE}."LastName"'
    type 'string'
  }
  dimension {
    name 'Title'
    title 'Job Title'
    description 'Employee job title (e.g. "Sales Representative")'
    sql '${CUBE}."Title"'
    type 'string'
  }
  dimension {
    name 'HireDate'
    title 'Hire Date'
    description 'Date employee was hired (group by year for hiring trends)'
    sql '${CUBE}."HireDate"'
    type 'time'
  }
  dimension {
    name 'City'
    title 'City'
    description 'Employee city'
    sql '${CUBE}."City"'
    type 'string'
  }
  dimension {
    name 'Country'
    title 'Country'
    description 'Employee country'
    sql '${CUBE}."Country"'
    type 'string'
  }
  dimension {
    name 'ReportsTo'
    title 'Manager Employee ID'
    description 'EmployeeID of the manager (NULL = top of org chart). A self-join is not supported here — group by this to count direct reports per manager ID, or use the warehouse cube, whose dim_employee carries manager_name.'
    sql '${CUBE}."ReportsTo"'
    type 'number'
  }

  // ── Dimensions: Territories (L2 via EmployeeTerritories) ───────────────
  dimension {
    name 'TerritoryDescription'
    title 'Territory'
    description 'Human-readable territory name'
    sql '@TERRITORIES@."TerritoryDescription"'
    type 'string'
  }

  // ── Dimensions: Region (L3 via Territories) ───────────────────────────
  dimension {
    name 'RegionDescription'
    title 'Sales Region'
    description 'Sales region'
    sql '@REGION@."RegionDescription"'
    type 'string'
  }

  // ── Measures ────────────────────────────────────────────────────────────
  // For group-level analysis (by country / title / territory / region). At
  // per-employee grain they degenerate to 1 or that employee's own value.
  measure {
    name 'EmployeeCount'
    title 'Employee Count'
    description 'Number of distinct employees. PICK THIS WHEN: grouping by country / title / territory / region. (At per-employee grain it always returns 1.)'
    sql '${CUBE}."EmployeeID"'
    type 'count_distinct'
  }
  measure {
    name 'AvgTenureYears'
    title 'Avg Tenure (years)'
    description "Average whole years since hire date. PICK THIS WHEN: grouping by title / country / segment."
    sql '@YEARS_HIRE@'
    type 'avg'
  }
  measure {
    name 'AvgAgeYears'
    title 'Avg Age (years)'
    description "Average age in whole years. PICK THIS WHEN: grouping by title / country / segment."
    sql '@YEARS_BIRTH@'
    type 'avg'
  }
  measure {
    name 'UniqueTerritories'
    title 'Unique Territories'
    description 'Number of distinct territories covered (requires the Territories join)'
    sql '@TERRITORIES@."TerritoryDescription"'
    type 'count_distinct'
  }
  measure {
    name 'UniqueSalesRegions'
    title 'Unique Sales Regions'
    description 'Number of distinct sales regions covered (requires the full 3-level chain)'
    sql '@REGION@."RegionDescription"'
    type 'count_distinct'
  }

  // ── Joins (3-level chain) ──────────────────────────────────────────────
  join {
    name '@EMPTERR@'
    parent 'CUBE'
    sql '${CUBE}."EmployeeID" = @EMPTERR@."EmployeeID"'
    relationship 'one_to_many'
  }
  join {
    name '@TERRITORIES@'
    parent '@EMPTERR@'
    sql '@EMPTERR@."TerritoryID" = @TERRITORIES@."TerritoryID"'
    relationship 'many_to_one'
  }
  join {
    name '@REGION@'
    parent '@TERRITORIES@'
    sql '@TERRITORIES@."RegionID" = @REGION@."RegionID"'
    relationship 'many_to_one'
  }

  // ── Segments ────────────────────────────────────────────────────────────
  segment {
    name 'executives'
    title 'Executives (no manager)'
    description 'Employees without a ReportsTo manager — top of org chart'
    sql '${CUBE}."ReportsTo" IS NULL'
  }
  segment {
    name 'individual_contributors'
    title 'Individual Contributors'
    description 'Employees who report to someone'
    sql '${CUBE}."ReportsTo" IS NOT NULL'
  }
  segment {
    name 'senior_tenure'
    title 'Senior Tenure (5+ years)'
    description 'Employees with 5 or more whole years of tenure'
    sql '@YEARS_HIRE@ >= 5'
  }
  segment {
    name 'recent_hires'
    title 'Recent Hires (≤2 years)'
    description 'Employees hired within the last 2 whole years'
    sql '@YEARS_HIRE@ <= 2'
  }

  // ── Hierarchies ─────────────────────────────────────────────────────────
  hierarchy {
    name 'employee_geography'
    title 'Employee Geography'
    levels 'Country', 'City', 'EmployeeName'
  }
  hierarchy {
    name 'sales_territory'
    title 'Sales Territory Hierarchy'
    levels 'RegionDescription', 'TerritoryDescription'
  }
  hierarchy {
    name 'org_chart'
    title 'Org Chart by Title'
    levels 'Title', 'EmployeeName'
  }
}
'''

String WAREHOUSE_DSL = '''// ═══════════════════════════════════════════════════════════════════════════
// Northwind Company Sales Warehouse cube  —  GENERATED by datasets/scripts/academy-cubes.groovy
// ═══════════════════════════════════════════════════════════════════════════
//
// Cube source: @FOL@ (the order-line grain fact)
// Scale: @SCALE@ — @FACTCOUNT@ fact rows
//
// THIS ONE IS NOT A COPY OF THE SAMPLE. The product's Sales Warehouse cube reads
// one flat denormalised view (vw_sales_detail) and needs no joins at all. The
// academy warehouse is a real star, so this cube is written against the fact and
// its dimensions — which is the point of Data Warehousing Series 2 - 25: reading
// a cube built over a star, not over a wide view.
//
// The star:
//   @FOL@          one row per order line
//   @DIMDATE@      the calendar, with fiscal periods, holidays and working days
//   @DIMCUST@      SCD Type 2 — a customer with a changed segment has more than one row
//   @DIMPROD@      SCD Type 2 — a repriced product has more than one row
//   @DIMEMP@       the sales force, carrying manager_name so the org chart needs no self-join
//   @DIMSHIP@      the couriers
//   @DIMCAT@       the product categories, joined through dim_product
//
// EVERY JOIN HERE IS AN INNER JOIN, and that is safe: no foreign key in the fact
// is NULL. Dates that did not happen (an order never shipped during the courier
// outage) point at the dimension's unknown member rather than at NULL, which is
// exactly the convention a star is supposed to use. Only order_date_key is joined
// to the calendar — joining the same dimension a second time for the shipped date
// would need an alias, which this DSL has no syntax for.
//
// JOIN graph:
//
//   @FOL@ (cube source)
//     ├── @DIMDATE@ (L1 — order_date_key)
//     ├── @DIMCUST@ (L1 — customer_key)
//     ├── @DIMPROD@ (L1 — product_key)
//     │     └── @DIMCAT@ (L2, parent=dim_product — category_key)
//     ├── @DIMEMP@  (L1 — employee_key)
//     └── @DIMSHIP@ (L1 — shipper_key)
// ═══════════════════════════════════════════════════════════════════════════

cube {
  sql_table '@FOL@'
  title 'Northwind Company Sales Warehouse (@SCALE@)'
  description 'Dimensional analysis over the Northwind Company star: order lines by date, customer, product, employee and shipper'

  // ── Dimensions: the fact itself (per-line browsing) ───────────────────
  dimension {
    name 'OrderID'
    title 'Order ID'
    description 'The order this line belongs to. Pick it for per-order browsing; leave it out to aggregate.'
    sql '${CUBE}."order_id"'
    type 'number'
  }
  dimension {
    name 'OrderStatus'
    title 'Order Status'
    description 'Open, Shipped or Cancelled, carried onto the fact as a degenerate dimension'
    sql '${CUBE}."order_status"'
    type 'string'
  }
  dimension {
    name 'Channel'
    title 'Channel'
    description 'How the order reached us, carried onto the fact as a degenerate dimension'
    sql '${CUBE}."channel"'
    type 'string'
  }

  // ── Dimensions: calendar (the drill path a star exists for) ───────────
  dimension {
    name 'Year'
    title 'Year'
    description 'Calendar year of the order date'
    sql '@DIMDATE@."year"'
    type 'number'
  }
  dimension {
    name 'Quarter'
    title 'Quarter'
    description 'Calendar quarter (1-4) of the order date'
    sql '@DIMDATE@."quarter"'
    type 'number'
  }
  dimension {
    name 'MonthName'
    title 'Month'
    description 'Month name of the order date'
    sql '@DIMDATE@."month_name"'
    type 'string'
  }
  dimension {
    name 'YearMonth'
    title 'Year-Month'
    description 'Year and month of the order date, sortable'
    sql '@DIMDATE@."year_month"'
    type 'string'
  }
  dimension {
    name 'FullDate'
    title 'Order Date'
    description 'The order date itself — the bottom of the calendar drill path'
    sql '@DIMDATE@."full_date"'
    type 'time'
  }
  dimension {
    name 'FiscalYear'
    title 'Fiscal Year'
    description 'Fiscal year of the order date. A star carries the fiscal calendar so nobody has to derive it.'
    sql '@DIMDATE@."fiscal_year"'
    type 'number'
  }
  dimension {
    name 'DayName'
    title 'Day of Week'
    description 'Day name of the order date'
    sql '@DIMDATE@."day_name"'
    type 'string'
  }

  // ── Dimensions: customer ───────────────────────────────────────────────
  dimension {
    name 'CustomerName'
    title 'Customer'
    description 'Customer company name, as it stood when the order was placed'
    sql '@DIMCUST@."company_name"'
    type 'string'
  }
  dimension {
    name 'CustomerCountry'
    title 'Customer Country'
    description 'Customer country'
    sql '@DIMCUST@."country"'
    type 'string'
  }
  dimension {
    name 'CustomerCity'
    title 'Customer City'
    description 'Customer city'
    sql '@DIMCUST@."city"'
    type 'string'
  }
  dimension {
    name 'CustomerSegment'
    title 'Customer Segment'
    description 'The segment IN FORCE WHEN THE ORDER WAS PLACED, not today. That is what Type 2 history buys you.'
    sql '@DIMCUST@."segment"'
    type 'string'
  }

  // ── Dimensions: product and category ──────────────────────────────────
  dimension {
    name 'ProductName'
    title 'Product'
    description 'Product name, as it stood when the order was placed'
    sql '@DIMPROD@."product_name"'
    type 'string'
  }
  dimension {
    name 'CategoryName'
    title 'Category'
    description 'Product category, joined through dim_product'
    sql '@DIMCAT@."category_name"'
    type 'string'
  }
  dimension {
    name 'SupplierName'
    title 'Supplier'
    description 'Supplier carried on the product dimension'
    sql '@DIMPROD@."supplier_name"'
    type 'string'
  }
  dimension {
    name 'SupplierCountry'
    title 'Supplier Country'
    description 'Supplier country carried on the product dimension'
    sql '@DIMPROD@."supplier_country"'
    type 'string'
  }
  dimension {
    name 'ListPrice'
    title 'List Price'
    description 'The catalogue price of that product version'
    sql '@DIMPROD@."list_price"'
    type 'number'
  }

  // ── Dimensions: employee and shipper ──────────────────────────────────
  dimension {
    name 'EmployeeName'
    title 'Sales Rep'
    description 'Employee who took the order'
    sql '@DIMEMP@."full_name"'
    type 'string'
  }
  dimension {
    name 'EmployeeTitle'
    title 'Rep Title'
    description 'Job title of the sales rep'
    sql '@DIMEMP@."title"'
    type 'string'
  }
  dimension {
    name 'ManagerName'
    title 'Manager'
    description "The rep's manager, pre-resolved on the dimension. No self-join needed — that is a thing a warehouse does for you."
    sql '@DIMEMP@."manager_name"'
    type 'string'
  }
  dimension {
    name 'ShipperName'
    title 'Shipper'
    description 'The courier'
    sql '@DIMSHIP@."company_name"'
    type 'string'
  }

  // ── Measures ────────────────────────────────────────────────────────────
  measure {
    name 'NetAmount'
    title 'Net Revenue'
    description 'Line revenue after discount, pre-computed on the fact. The headline measure.'
    sql '${CUBE}."net_amount"'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'GrossAmount'
    title 'Gross Revenue'
    description 'Line revenue before discount, pre-computed on the fact'
    sql '${CUBE}."gross_amount"'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'DiscountGiven'
    title 'Discount Given'
    description 'Gross minus net — what the discounting cost, in money rather than in percent'
    sql '(${CUBE}."gross_amount" - ${CUBE}."net_amount")'
    type 'sum'
    format 'currency'
  }
  measure {
    name 'Quantity'
    title 'Units Sold'
    description 'Units sold across the lines in scope'
    sql '${CUBE}."quantity"'
    type 'sum'
  }
  measure {
    name 'OrderCount'
    title 'Order Count'
    description 'Number of distinct orders. The fact is at LINE grain, so count orders distinctly or you will count lines.'
    sql '${CUBE}."order_id"'
    type 'count_distinct'
  }
  measure {
    name 'LineCount'
    title 'Line Count'
    description 'Number of fact rows in scope'
    type 'count'
  }
  measure {
    name 'AvgDiscount'
    title 'Average Discount'
    description 'Average discount rate across the lines in scope (0.0 to 1.0)'
    sql '${CUBE}."discount"'
    type 'avg'
  }
  measure {
    name 'UniqueCustomers'
    title 'Unique Customers'
    description 'Number of distinct customer VERSIONS. With Type 2 history a customer who changed segment counts twice — add the current_customers segment if you want people, not versions.'
    sql '${CUBE}."customer_key"'
    type 'count_distinct'
  }

  // ── Joins ───────────────────────────────────────────────────────────────
  join {
    name '@DIMDATE@'
    parent 'CUBE'
    sql '${CUBE}."order_date_key" = @DIMDATE@."date_key"'
    relationship 'many_to_one'
  }
  join {
    name '@DIMCUST@'
    parent 'CUBE'
    sql '${CUBE}."customer_key" = @DIMCUST@."customer_key"'
    relationship 'many_to_one'
  }
  join {
    name '@DIMPROD@'
    parent 'CUBE'
    sql '${CUBE}."product_key" = @DIMPROD@."product_key"'
    relationship 'many_to_one'
  }
  join {
    name '@DIMCAT@'
    parent '@DIMPROD@'
    sql '@DIMPROD@."category_key" = @DIMCAT@."category_key"'
    relationship 'many_to_one'
  }
  join {
    name '@DIMEMP@'
    parent 'CUBE'
    sql '${CUBE}."employee_key" = @DIMEMP@."employee_key"'
    relationship 'many_to_one'
  }
  join {
    name '@DIMSHIP@'
    parent 'CUBE'
    sql '${CUBE}."shipper_key" = @DIMSHIP@."shipper_key"'
    relationship 'many_to_one'
  }

  // ── Segments ────────────────────────────────────────────────────────────
  segment {
    name 'current_customers'
    title 'Current Customer Version'
    description 'Only the customer row that is in force today. Turn it on and off beside a segment breakdown to see what Type 2 history changes.'
    sql '${CUBE}."customer_key" IN (SELECT customer_key FROM @DIMCUST@ WHERE is_current)'
  }
  segment {
    name 'current_products'
    title 'Current Product Version'
    description 'Only the product row that is in force today'
    sql '${CUBE}."product_key" IN (SELECT product_key FROM @DIMPROD@ WHERE is_current)'
  }
  segment {
    name 'shipped'
    title 'Shipped Orders'
    description 'Lines whose order reached Shipped'
    sql '${CUBE}."order_status" = \\'Shipped\\''
  }
  segment {
    name 'cancelled'
    title 'Cancelled Orders'
    description 'Lines whose order was cancelled'
    sql '${CUBE}."order_status" = \\'Cancelled\\''
  }
  segment {
    name 'discounted'
    title 'Discounted Lines'
    description 'Lines that were sold at a discount'
    sql '${CUBE}."discount" > 0'
  }
  segment {
    name 'working_days'
    title 'Ordered On A Working Day'
    description 'Lines whose order date is a working day in the calendar dimension'
    sql '${CUBE}."order_date_key" IN (SELECT date_key FROM @DIMDATE@ WHERE is_working_day)'
  }

  // ── Hierarchies (the four analytical moves live here) ─────────────────
  hierarchy {
    name 'calendar'
    title 'Calendar'
    levels 'Year', 'Quarter', 'MonthName', 'FullDate'
  }
  hierarchy {
    name 'customer_geography'
    title 'Customer Geography'
    levels 'CustomerCountry', 'CustomerCity', 'CustomerName'
  }
  hierarchy {
    name 'product_taxonomy'
    title 'Product Taxonomy'
    levels 'CategoryName', 'ProductName'
  }
  hierarchy {
    name 'sales_org'
    title 'Sales Organisation'
    levels 'ManagerName', 'EmployeeName'
  }
}
'''

// ─────────────────────────────────────────────────────────────────────────────────────────────
// FIGURES FOR THE HEADER COMMENTS — counted, not assumed, so a regenerated cube always describes
// the data it is actually pointed at.
// ─────────────────────────────────────────────────────────────────────────────────────────────
Closure<String> count1 = { String sql ->
    try { return (dbSql.firstRow(sql)?.n ?: '?').toString() } catch (Exception e) { return '?' }
}
String ORDERS_Q = qt(OLTP, 'Orders'), OD_Q = qt(OLTP, 'Order Details')
String orderCount     = count1("select count(*) as n from ${ORDERS_Q}".toString())
String lineCount      = count1("select count(*) as n from ${OD_Q}".toString())
String customerCount  = count1("select count(*) as n from ${qt(OLTP, 'Customers')}".toString())
String productCount   = count1("select count(*) as n from ${qt(OLTP, 'Products')}".toString())
String categoryCount  = count1("select count(*) as n from ${qt(OLTP, 'Categories')}".toString())
String employeeCount  = count1("select count(*) as n from ${qt(OLTP, 'Employees')}".toString())
String territoryCount = count1("select count(*) as n from ${qt(OLTP, 'Territories')}".toString())
String regionCount    = count1("select count(*) as n from ${qt(OLTP, 'Region')}".toString())
String levelCount     = count1(("select count(distinct lvl) as n from (" +
                               "with recursive org(id, lvl) as (" +
                               "  select \"EmployeeID\", 1 from ${qt(OLTP, 'Employees')} where \"ReportsTo\" is null" +
                               "  union all" +
                               "  select e.\"EmployeeID\", org.lvl + 1 from ${qt(OLTP, 'Employees')} e join org on e.\"ReportsTo\" = org.id" +
                               ") select lvl from org) t").toString())
String factCount      = withWarehouse ? count1("select count(*) as n from ${qt(STAR, 'fact_order_line')}".toString()) : '0'

// ─────────────────────────────────────────────────────────────────────────────────────────────
// SUBSTITUTION — one pass, every marker, the same map for every cube.
// ─────────────────────────────────────────────────────────────────────────────────────────────
Map<String, String> M = [
    '@ORDERS@'        : ORDERS_Q,
    '@OD@'            : OD_Q,
    '@CUSTOMERS@'     : qt(OLTP, 'Customers'),
    '@PRODUCTS@'      : qt(OLTP, 'Products'),
    '@CATEGORIES@'    : qt(OLTP, 'Categories'),
    '@SUPPLIERS@'     : qt(OLTP, 'Suppliers'),
    '@SHIPPERS@'      : qt(OLTP, 'Shippers'),
    '@EMPLOYEES@'     : qt(OLTP, 'Employees'),
    '@EMPTERR@'       : qt(OLTP, 'EmployeeTerritories'),
    '@TERRITORIES@'   : qt(OLTP, 'Territories'),
    '@REGION@'        : qt(OLTP, 'Region'),
    '@FOL@'           : qt(STAR, 'fact_order_line'),
    '@DIMDATE@'       : qt(STAR, 'dim_date'),
    '@DIMCUST@'       : qt(STAR, 'dim_customer'),
    '@DIMPROD@'       : qt(STAR, 'dim_product'),
    '@DIMEMP@'        : qt(STAR, 'dim_employee'),
    '@DIMSHIP@'       : qt(STAR, 'dim_shipper'),
    '@DIMCAT@'        : qt(STAR, 'dim_category'),
    '@YEARS_HIRE@'    : yearsSince('${CUBE}."HireDate"'),
    '@YEARS_BIRTH@'   : yearsSince('${CUBE}."BirthDate"'),
    '@SCALE@'         : SCALE,
    '@ORDERCOUNT@'    : orderCount,
    '@LINECOUNT@'     : lineCount,
    '@CUSTOMERCOUNT@' : customerCount,
    '@PRODUCTCOUNT@'  : productCount,
    '@CATEGORYCOUNT@' : categoryCount,
    '@EMPLOYEECOUNT@' : employeeCount,
    '@LEVELCOUNT@'    : levelCount,
    '@TERRITORYCOUNT@': territoryCount,
    '@REGIONCOUNT@'   : regionCount,
    '@FACTCOUNT@'     : factCount,
]

Closure<String> fill = { String template ->
    String out = template
    M.each { k, v -> out = out.replace(k, v) }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WRITE
// ─────────────────────────────────────────────────────────────────────────────────────────────
Closure<String> xmlEscape = { String s ->
    s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
}

Path cubesRoot = Paths.get(CUBES_DIR).toAbsolutePath().normalize()
Files.createDirectories(cubesRoot)

List<Map<String, String>> CUBES = [
    [id: "${PREFIX}-sales",     name: "Northwind Company Sales Analysis (${SCALE})",
     desc: "Order, line-item, customer, employee and product analysis over Northwind Company at scale ${SCALE}. The sample Sales cube's design, on a company with five years of trading in it.",
     dsl: SALES_DSL],
    [id: "${PREFIX}-inventory", name: "Northwind Company Product Inventory (${SCALE})",
     desc: "Product catalogue, stock levels, cost and suppliers. Carries UnitCost, so stock can be valued at cost as well as at retail.",
     dsl: INVENTORY_DSL],
    [id: "${PREFIX}-customers", name: "Northwind Company Customer Management (${SCALE})",
     desc: "CRM view of the customer base: who they are, where they are, how active they are and what they spend.",
     dsl: CUSTOMERS_DSL],
    [id: "${PREFIX}-hr",        name: "Northwind Company Human Resources (${SCALE})",
     desc: "Workforce composition, tenure and territory coverage. Unlike the frozen sample, the territory and region tables here are populated.",
     dsl: HR_DSL],
]
if (withWarehouse) {
    CUBES << [id: "${PREFIX}-warehouse", name: "Northwind Company Sales Warehouse (${SCALE})",
              desc: "Dimensional analysis over the Northwind Company star: order lines by date, customer, product, employee and shipper, with Type 2 history on customer and product.",
              dsl: WAREHOUSE_DSL]
}

List<String> written = []
CUBES.each { Map<String, String> c ->
    Path dir = cubesRoot.resolve(c.id)
    Files.createDirectories(dir)

    String xml = """<?xml version="1.0" encoding="UTF-8"?>
<cube>
    <name>${xmlEscape(c.name)}</name>
    <description>${xmlEscape(c.desc)}</description>
    <connectionId>${xmlEscape(CONNECTION)}</connectionId>
</cube>
"""
    Files.write(dir.resolve('cube.xml'), xml.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8))
    Files.write(dir.resolve("${c.id}-cube-config.groovy"),
                fill(c.dsl).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8))
    written << c.id
    log.info("academy-cubes: wrote ${dir}")
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// REPORT
// ─────────────────────────────────────────────────────────────────────────────────────────────
log.info("")
log.info("academy-cubes: ${written.size()} cube(s) written to ${cubesRoot}")
written.each { log.info("  - ${it}") }
log.info("  operational schema : ${OLTP}  (${orderCount} orders, ${lineCount} order lines, ${customerCount} customers, ${employeeCount} employees)")
if (withWarehouse) {
    log.info("  star schema        : ${STAR}  (${factCount} fact rows)")
} else {
    log.info("  star schema        : ${STAR} NOT INSTALLED — the warehouse cube was skipped")
}
if (!CONNECTION) {
    log.warn("  connectionId is EMPTY — this script was run outside the app, so nothing knew which " +
             "connection to bind. Set it in each cube.xml, or re-run from the Seed Data tab.")
} else {
    log.info("  connectionId       : ${CONNECTION}")
}
log.info("")
log.info("The product's five samples in config/samples-cubes were not touched.")
